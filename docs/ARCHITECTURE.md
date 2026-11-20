# Arquitetura proposta

> **Estado:** as fases 1 a 9 implementaram `core`, `cartography`, `world`, `discovery`, `storage`, composição cliente, HUD 2D, renderizador 3D, camadas/cutaway, LOD híbrido e sincronização incremental de mudanças. Este documento descreve a implementação real; otimizações finais permanecem para a Fase 10.
> As regras normativas estão em [`AGENTS.md`](../AGENTS.md). Este documento traduz essas regras em fronteiras concretas de código.

## 1. Princípio central

O **modelo cartográfico persistente** é a única fonte da verdade. Minimap, malhas 3D, LOD, câmera, camadas e cutaway são projeções descartáveis, reconstruíveis a partir dele. Nenhuma projeção alimenta o modelo nem serve como fonte independente de dados de mundo.

Consequência prática: todo dado de mundo que entra no modelo passa por um único caminho de escrita (coleta → descoberta ou reconciliação → aplicação), e todo consumidor lê por snapshots imutáveis com versão.

## 2. Fronteiras e direção das dependências

```text
 [config]  [input]  [gui]
      \       |      /
       \      v     /
        [core: MapManager / MapContext]
                   |
                   v
 [world] -> [discovery] --(única escrita)--> [cartography: fonte da verdade]
                                                    |
                                                    v  snapshots imutáveis
                          [terrain] [layers] [cutaway] [lod]
                                                    |
                                                    v
                          [rendering.minimap] [rendering.worldmap]
```

Regras de dependência:

- `cartography` não depende de renderização, GUI, disco, câmera, LOD nem cortes; depende apenas de tipos do JDK e de identificadores do Minecraft.
- `world` e `discovery` são os **únicos produtores** de mudanças no modelo. As leituras de mundo vivo acontecem apenas na game thread: `ChunkDataCollector` acessa chunks já carregados e produz `VoxelSample`s imutáveis; `DiscoveryEngine` entrega observações novas à `DiscoveryPolicy`, enquanto `BlockUpdateSynchronizer` reconcilia somente posições já conhecidas. Nenhum snapshot carrega referência viva de `Level`, `ChunkAccess` ou `BlockState`.
- `storage` serializa o modelo, mas a renderização nunca lê arquivos nem o estado mutável do storage.
- `terrain`, `layers`, `cutaway` e `lod` derivam dados; suas caches usam revisão por chunk, LOD e parâmetros imutáveis de apresentação.
- `rendering` e `gui` nunca acessam `Level`/`ChunkAccess`, nem fazem I/O, nem geram malhas.

## 3. Mapa de pacotes alvo

Topologia alvo, **criada incrementalmente**. Pacotes e classes só aparecem quando a responsabilidade se torna necessária — sem diretórios vazios ou placeholders (`AGENTS.md` §3.1, §16.1).

| Pacote | Responsabilidade | Fase |
|---|---|---:|
| `com.czminimap` | entrypoint comum (`MOD_ID`, constantes); nenhuma referência a classes de cliente | 1 |
| `com.czminimap.client` | eventos `DIST.CLIENT`, resolvedor de identidade e `ClientMapContext` (composition root) | 1/3 |
| `com.czminimap.config` | `ModConfigSpec` de apresentação, descoberta e performance | 1 |
| `com.czminimap.input` | `KeyMapping`s e ações de teclado | 1 |
| `com.czminimap.core` | `MapManager` (implementado), identidade de mundo/dimensão; `MapContext` permanece proposto | 2/3 |
| `com.czminimap.cartography` | modelo persistente: regiões, seções, voxels, estados, snapshots | 2 |
| `com.czminimap.world` | coleta imutável, chaves de posição e `BlockUpdateSynchronizer` incremental | 3/9 |
| `com.czminimap.discovery` | `DiscoveryPolicy`, `ExplorationTracker`, `DiscoveryEngine`, `DiscoverySettings`, `DiscoveryResult` | 3 |
| `com.czminimap.storage` | `RegionCodec` (`CZM1`/LZ4/CRC32), `MapStorage` (carga e escrita assíncronas, atômica, com backup e quarentena) | 4 |
| `com.czminimap.terrain` | proposto: agregação por região e material resolver com atlas — a Fase 6 manteve a geração de malha em `rendering.worldmap` | 6 (parcial) |
| `com.czminimap.layers` | `LayerRange`, `LayerResolver`, modos/direção e `LayerVisibility` imutável | 7 |
| `com.czminimap.cutaway` | `CutawaySettings`, planos horizontal/vertical, eixo/lado e `ClipResolver` | 7 |
| `com.czminimap.lod` | `LodLevel` 0..3 e `LodSelector` por tamanho projetado/distância | 8 |
| `com.czminimap.rendering.minimap` | `MinimapColorResolver`, `MinimapImage`, `MinimapLayout` (puros) e `MinimapRenderer` (GPU) | 5 |
| `com.czminimap.rendering.worldmap` | câmera, agregação voxel-alinhada, cache LRU/assíncrono e desenho das malhas | 6–8 |
| `com.czminimap.gui` | mapa 3D, controles de camadas/cutaway e telemetria de LOD/jobs/memória | 6–8 |

## 4. Modelo cartográfico

### 4.1 Identidades

- `WorldKey` — `storageId` + `displayName`, resolvidos no cliente. O modo padrão `NAME_AND_HASH` combina o nome/pasta sanitizado com um SHA-256 truncado (12 hex) da identidade normalizada; `HASH_ONLY` e `NAME_ONLY` são configuráveis via `storage.world_identity_mode`. Para singleplayer a identidade usa nome do mundo + seed; para multiplayer usa host/porta normalizados. **Endereço e seed nunca são persistidos em claro**, e trocar o modo cria outro diretório (sem migração automática).
- `DimensionKey` — `ResourceLocation` da dimensão **mais** `minY`/`maxYExclusive` reais (`ClientLevel#getMinBuildHeight`/`getMaxBuildHeight`). Nada de limites do Overworld hardcoded.
- `RegionKey` — `WorldKey` + `DimensionKey` + coordenadas de região (32 × 32 chunks = 512 blocos). Unidade de sparse storage, dirty tracking e cache.
- Seções — mapa esparso de seções 16³ dentro de cada chunk; alocadas somente quando há voxel descoberto. A chave de chunk é um `long` derivado das coordenadas (equivalente a `ChunkPos.asLong`), sem reter objetos vivos de mundo.

### 4.2 Voxel, estados e tempo de observação

Para evitar um objeto por bloco, cada região mantém:

- uma **paleta por região** de `VoxelAppearance` (`block id` + máscara de propriedades relevantes + `biome id` + tint), deduplicada por igualdade de record;
- arrays esparsos de índices de paleta por seção (`int[4096]`, `-1` = sem aparência) — a implantação atual usa arrays completos por seção alocada, não bitsets;
- array de estados por seção (`byte[4096]`, ordinal de `DiscoveryState`);
- `lastObservedTick` por seção, monotonicamente atualizado; escritas repetidas sem mudança de estado/aparência não incrementam a versão do modelo.

Bitsets e compactação adicional ficam como otimização futura, a introduzir quando o perfil de memória justificar.

Estados propostos:

```java
UNKNOWN            // nunca descoberto — deve permanecer oculto
KNOWN_AIR          // sabidamente vazio (diferente de UNKNOWN)
SOLID              // bloco sólido descoberto
EXPOSED_SURFACE    // superfície exposta descoberta
UNDERGROUND_SPACE  // espaço/cavidade subterrânea descoberta
```

"Descoberto anteriormente, mas não observado agora" **não** é um quinto estado: é a combinação de um estado descoberto com `lastObservedTick` antigo. Isso evita estados contraditórios e permite envelhecimento visual sem perder informação.

Forma ilustrativa (a ajustar ao implementar):

```java
record DimensionKey(ResourceLocation id, int minY, int maxYExclusive) {}
record RegionKey(WorldKey world, DimensionKey dimension, int rx, int rz) {}
record VoxelAppearance(ResourceLocation block, int propertiesMask,
                       ResourceLocation biome, int tint) {}
```

### 4.3 Snapshots

`CartographicSnapshot` carrega `version`, revisões por chunk, um mapa imutável de regiões e um **índice derivado de colunas superficiais** (`SurfaceColumnKey -> SurfaceColumn`). O índice é mantido por `CartographicMap.setVoxel`, inclusive ao remover o topo de uma coluna, e reconstruído em `loadRegion`; ele não é persistido nem tratado como fonte independente. Paleta, chunks, seções, arrays, revisões e índice são copiados na construção. A versão global continua ordenando persistência; HUD e malhas consultam revisões espaciais para não reagir a mudanças distantes.

## 5. Fluxos de dados

### Escrita (game thread)

```text
ClientTickEvent.Post (game thread)
  -> ClientMapContext resolve WorldKey/DimensionKey e limites de config
  -> DiscoveryEngine: MapManager seleciona mundo; ExplorationTracker fornece posições
  -> ChunkDataCollector lê chunks já carregados -> VoxelSample imutáveis
  -> WorldDataSnapshot -> DiscoveryPolicy -> DiscoveryResult
  -> CartographicMap.setVoxel (único ponto de escrita)
  -> regiões afetadas marcadas como dirty
  -> storage recebe job assíncrono deduplicado (flush periódico/desconexão/shutdown)
  -> versão + revisão do chunk incrementadas

Mudança já descoberta:
ClientLevel/chunk carregado -> BlockUpdateSynchronizer (budget + raio)
  -> somente posições cujo estado cartográfico != UNKNOWN
  -> CartographicMap.setVoxel -> dirty + revisão espacial
  -> HUD invalida apenas se a revisão estiver na área visível
  -> malha invalida o chunk e vizinhos usados no culling
```

### Leitura (render)

```text
CartographicSnapshot(version, chunk revisions)
  -> worker: gerar malha/LOD (somente dados imutáveis)
  -> cache por chave (chunk, revisão, LOD, LayerVisibility)
  -> render thread: somente upload/draw do recurso já pronto
```

A HUD e o mapa 3D consomem o mesmo snapshot; nenhum dos dois escaneia o mundo.

## 6. Threading

| Onde | Permitido | Proibido |
|---|---|---|
| Game thread | acessar `Level`/chunks, tracking do jogador, aplicar descoberta, handlers de evento, mudanças de config | I/O de disco, geração de malha, upload GPU |
| Render thread | desenhar e fazer upload/destroy de recursos GPU | I/O, acesso a mundo mutável, geração de geometria |
| Workers limitados | serialização/compressão, construção de malha a partir de snapshot, LOD, indexação | acessar mundo vivo, tocar GPU |

`WorldMapMeshCache` usa até dois workers daemon e fila limitada. Jobs são deduplicados por geração/chunk/revisão/LOD; resultados obsoletos são descartados. `mesh_budget` limita submissões e publicações por tick. Storage mantém seu worker separado.

## 7. Persistência (Fase 4 — implementada)

- Local: `<gameDir>/czminimap/<storageId>/dimensions/<namespace>/<path>/regions/r.<x>.<z>.bin`. `storageId` é o identificador local do mundo (nome sanitizado + hash configurável; modos `HASH_ONLY`/`NAME_ONLY` também são suportados). Nenhum endereço, seed ou credencial é gravado.
- Cabeçalho `CZM1`: magic (4 bytes), `schemaVersion` (int), `storageId`, id da dimensão, `minY`, `maxYExclusive`, coordenadas da região, tamanhos bruto/comprimido e CRC32. O payload contém paleta + chunks + seções (arrays de estados e índices de paleta + `lastObservedTick`), comprimido com **LZ4**. lz4-java 1.8.0 é declarado no build e embutido via `jarJar`, mantendo o mod standalone.
- Toda leitura valida magic, versão, limites, tamanhos, CRC, coordenadas de chunk dentro da região, limites de Y da dimensão, ordinais de estado e índices de paleta. Versão futura = falha controlada; cadeia de migração ficará para quando existir um schema v2.
- Escrita: `.tmp` no mesmo diretório → cópia do arquivo anterior para `.bak` → `ATOMIC_MOVE` com fallback para `REPLACE_EXISTING`. Se o arquivo principal estiver ausente ou inválido, o carregador tenta `.tmp` e `.bak` e promove o primeiro válido. Inválidos vão para `.corrupt`.
- `MapStorage` usa um único worker daemon. A fila de escrita tem limite de 256 regiões e deduplicação por `RegionKey`; quando está cheia, a região continua suja e é reenviada no próximo flush. `flush()` espera as escritas enfileiradas; `close()` faz flush e encerra o worker.
- Carga: `requestLoad(world)` varre todas as dimensões salvas no worker e publica um `LoadBatch` com token. `ClientMapContext` pausa a descoberta até aplicar o batch no game thread (gate), remapeando a `WorldKey` ativa porque o nome de exibição não é persistido. Dados salvos são mesclados por seção usando `lastObservedTick`: observações mais novas nunca são sobrescritas por dados antigos.
- O relógio de observação é monotônico entre sessões (`CartographicMap.clock()`), evitando que um tick reiniciado no cliente faça dados antigos vencerem descobertas novas.
- Flush: a cada `storage.flush_interval_seconds` (config, padrão 30 s), na troca de mundo, em `ClientPlayerNetworkEvent.LoggingOut` e em `GameShuttingDownEvent`. Nenhum I/O acontece na render thread.

## 8. Descoberta

- **Superfície (implementado):** cursor espiral sobre colunas no raio configurado; `WORLD_SURFACE` fornece o topo, amostrado junto com até dois blocos abaixo. Topo = `EXPOSED_SURFACE`; inferiores expostos lateralmente = `SOLID`. Sem raycast.
- **Subterrâneo (implementado):** BFS limitado a partir do bloco do jogador e seus 6 vizinhos. Ar conectado abaixo do heightmap = `UNDERGROUND_SPACE`; acima dele = `KNOWN_AIR` sem expansão. Sólidos encontrados na fronteira = `SOLID`. Blocos internos de montanha não são revelados por estar em chunk carregado.
- **Orçamento:** `max_samples_per_tick` divide-se entre ar e superfície; fila de ar limitada a 16.384 posições; rebase do cursor após 8 blocos de deslocamento, mudança de raio, dimensão ou mundo.
- **Estado:** `UNKNOWN` nunca é escrito no modelo; troca de dimensão preserva dados, troca de mundo troca o mapa. A persistência (Fase 4) salva e recarrega o estado descoberto por mundo/dimensão; a descoberta fica pausada até a carga terminar.
- Ainda não implementado: visibilidade direcional (olhar) fora da conectividade de ar e medição de frame time em mundo grande.

## 9. Representação 3D, camadas, cutaway e LOD

### Implementado na Fase 6

- `CartographicSnapshot.forEachSolid` percorre apenas `SOLID`/`EXPOSED_SURFACE` de uma região, entregando coordenadas, estado e `VoxelAppearance` imutáveis. `UNKNOWN`, `KNOWN_AIR` e `UNDERGROUND_SPACE` nunca viram geometria.
- `TerrainMeshBuilder` gera uma malha por **chunk** (não por bloco) a partir do snapshot. Face é removida quando o vizinho é sólido conhecido, incluindo vizinhos do chunk adjacente (a amostragem cobre a vizinhança 3×3 de chunks). Cada vértice carrega posição + RGBA (7 floats).
- `WorldMapMaterialResolver` converte `VoxelAppearance` em cor ARGB por face reaproveitando `MinimapColorResolver`; o sombreamento é fixo por direção (UP mais claro, DOWN mais escuro, lados intermediários). Não há atlas, `BlockState` vivo, tint dinâmico nem texturas — decisão explícita desta fase.
- A Fase 6 introduziu um LRU por chunk e orçamento por tick; a Fase 8 substituiu sua validade global e construção síncrona por revisão espacial e workers, descritos abaixo. A retenção quadrada e o ajuste de raio à capacidade permanecem.
- `WorldMapRenderer.tick` atualiza câmera e viewport, publica resultados prontos e submete no máximo `mesh_budget` jobs. A construção de geometria não roda mais na game thread.
- `WorldMapRenderer.render` (render thread) salva a projeção GUI, zera a translação GUI da pilha model-view, aplica `view` + projeção perspectiva, desenha as malhas com `RenderType` próprio (`POSITION_COLOR`/QUADS, `NO_CULL`, depth test) e o marcador do jogador, depois restaura projeção e model-view. Nenhuma malha é gerada nesse caminho.
- `WorldMapScreen` mantém interação de câmera separada dos widgets: botão esquerdo rotaciona, meio/direito deslocam, roda aplica zoom, `ESC` fecha e sincroniza `ClientUiState`; cliques e scroll sobre o painel nunca movem a câmera.

### Implementado na Fase 7

- `LayerResolver` divide `[dimension.minY, dimension.maxYExclusive)` em `LayerRange`s half-open configuráveis, sempre ancorados no limite real da dimensão. O último intervalo é truncado; `indexForY` e `clampIndex` tratam alturas negativas e troca de dimensão.
- `LayerVisibility` carrega dimensão, modo, lista imutável de intervalos e cutaway. `SELECTED` contém um intervalo; `CUMULATIVE` contém vários intervalos contíguos para `UP` ou `DOWN`; `FULL` e `CUTAWAY` cobrem todos os intervalos. O predicado só decide visibilidade e nunca modifica coordenadas ou dados cartográficos.
- `CutawaySettings` combina opcionalmente um plano horizontal (`y < maxYExclusive`) e um plano vertical constante em `X` ou `Z`, mantendo o lado negativo (`<`) ou positivo (`>=`). `ClipResolver` é puro e opera em coordenadas reais do mundo.
- `TerrainMeshBuilder` aplica `LayerVisibility` tanto antes de emitir um voxel quanto ao montar o conjunto de vizinhos para culling. Um bloco conhecido ocultado pelo filtro deixa de ocluir a face de corte do bloco conhecido visível. Como a entrada continua sendo `forEachSolid`, o corte não materializa `UNKNOWN` ou ar conhecido.
- `WorldMapRenderer` resolve a seleção a partir do jogador, limita-a ao `DimensionKey`, consome atalhos e reconstrói somente malhas de apresentação. `render` aceita apenas a visualização e revisão atuais; mudanças do raio efetivo também invalidam a viewport, e o conjunto solicitado nunca excede a capacidade configurada.
- O painel expõe modo, direção cumulativa, intervalo anterior/próximo, habilitação dos dois planos, eixo, lado e deslocamento do plano vertical. A faixa selecionada e o span efetivamente visível são mostrados com Y real.

### Implementado na Fase 8

- `LodLevel` define escalas horizontais voxel-alinhadas 1/2/4/8. `LodSelector` estima pixels por bloco a partir de distância, altura da viewport e FOV, limitado por `rendering.lod_quality`.
- `TerrainMeshBuilder` agrega uma fatia Y de cada vez. Uma célula só vira prisma maior quando todos os voxels da área são sólidos, conhecidos e visíveis; qualquer `KNOWN_AIR`, `UNKNOWN`, irregularidade ou limite de cutaway subdivide até LOD 0. Cavernas e coordenadas Y reais não são preenchidas nem deslocadas.
- Faces agregadas consultam todos os vizinhos cobertos, inclusive em chunks adjacentes. Diferentes níveis compartilham limites inteiros; durante a troca de nível a malha anterior válida continua visível até o job novo terminar.
- `WorldMapMeshCache` constrói em background a partir de `CartographicSnapshot`, limita entradas por `max_cached_chunks` e bytes por `mesh_memory_mb`, deduplica jobs e descarta resultados obsoletos. O painel mostra L0..L3, jobs e memória estimada.
- `CartographicSnapshot.meshRevision` combina o chunk com a vizinhança 3×3 usada para culling. Mudança distante não invalida a malha; mudança de borda invalida os vizinhos necessários.

### Implementado na Fase 9

- `BlockUpdateSynchronizer` percorre de forma justa e limitada voxels já descobertos em chunks carregados ao redor do jogador. Posições por chunk ficam cacheadas até a revisão mudar; `synchronization.scan_radius_chunks` e `max_samples_per_tick` limitam o trabalho.
- Amostras podem atualizar sólido, ar ou `VoxelAppearance`, mas uma posição `UNKNOWN` é sempre ignorada. O polling funciona em singleplayer e multiplayer sem evento servidor, pedido de chunk ou inferência de terreno.
- Remover o topo de uma coluna reconstrói o índice superficial. A versão global continua acionando persistência, enquanto HUD e malhas usam revisões espaciais para evitar rebuild distante.

### Ainda proposto

- Material resolver com UV do atlas + tint de biome + transparência e invalidação em resource reload; a aparência 3D permanece cor por face.

## 10. Minimap 2D (Fase 5 — implementada)

- Evento: `RenderGuiEvent.Post` no barramento de eventos de cliente (NeoForge 21.1.267); o desenho é ignorado quando não há jogador/nível, quando uma tela está aberta ou quando o mapa 3D está aberto.
- `MinimapRenderer.tick` roda na game thread e compara `CartographicMap.version()` com a versão em cache. Só chama `snapshot()` e reconstrói o buffer quando a versão ou os parâmetros (centro, zoom, tamanho, forma, mundo/dimensão) mudam; há throttle mínimo de 100 ms entre reconstruções.
- `MinimapImage` é um `int[]` ARGB imutável derivado apenas de `CartographicSnapshot.surfaceAt(...)`. Colunas sem superfície descoberta recebem a cor de `UNKNOWN`; ar conhecido e cavidades não são desenhados como terreno. Máscara circular e moldura são aplicadas no próprio buffer, com alfa zero fora do círculo.
- `MinimapRenderer.render` roda na render thread: converte ARGB → ABGR, escreve em `NativeImage`, chama `DynamicTexture.upload()` e desenha com `GuiGraphics.blit`. Nenhum acesso a mundo, disk ou geração de geometria acontece nesse caminho.
- Orientação: o buffer é sempre norte-acima. Para `north_up = false`, a pose é rotacionada por `π - yaw` em torno do centro; o indicador do jogador é desenhado fora dessa rotação com `π + yaw` (norte-acima) ou sem rotação (cabeçalho-acima). A bússola é desenhada dentro da pose rotacionada, então acompanha o mapa.
- Posicionamento: `MinimapLayout.resolve` calcula os oito âncoras em coordenadas GUI-escaladas; `CUSTOM` usa `custom_x`/`custom_y`. Nenhuma posição depende da janela ou da câmera 3D.
- Recursos GPU: uma única `DynamicTexture` é registrada em `czminimap:hud_minimap`, recriada quando o tamanho muda e liberada em shutdown. Troca de mundo/dimensão invalida apenas o cache de pixels.

## 11. Configuração e input

- Config apenas `ModConfig.Type.CLIENT`, com `ModConfigSpec` e validação de intervalos. Apresentação separada de dados persistentes; mudar config nunca apaga mapa. Arquivos distintos: `czminimap-hud-client.toml` e `czminimap-map-client.toml`. `worldmap` expõe FOV, raio, sensibilidade, limite de chunks, orçamento por tick, `mesh_memory_mb` e fundo; `rendering.lod_quality` limita a simplificação; `synchronization` limita raio e amostras; `layers` controla intervalos e modo.
- `KeyMapping`s registrados via `RegisterKeyMappingsEvent` no barramento do mod, somente em classe `DIST.CLIENT`; checagem em `ClientTickEvent.Post` com `consumeClick()`. Categoria própria `key.categories.czminimap` e traduções `en_us`/`pt_br`. As classes usam `@EventBusSubscriber(value = Dist.CLIENT)` sem o atributo `bus`, deprecado e removido no NeoForge 1.21.1 — o barramento é inferido pelo tipo de evento.
- Ações: abrir/fechar mapa, alternar HUD, zoom da HUD, reset de câmera, retornar ao jogador e navegação de camadas. Defaults são provisórios e serão validados contra conflitos na Fase 1.

## 12. Decisões de design e justificativas

| Decisão | Justificativa |
|---|---|
| Fonte única de verdade + snapshots versionados | impede divergência entre HUD e mapa 3D e permite workers sem locks |
| Paleta + bitsets por região | escala melhor que um objeto por bloco e preserva `UNKNOWN` vs `KNOWN_AIR` |
| Campo temporal separado do estado | evita combinações contraditórias de flags de descoberta |
| Um único ponto de escrita no modelo | facilita a invalidação, a persistência e a auditoria do que foi descoberto |
| Entrypoint comum sem imports de cliente | mantém classes de cliente fora do caminho do servidor dedicado |
| LZ4 embutido via `jarJar` | compressão rápida para regiões esparsas sem exigir instalação separada; alternativa JDK era mais lenta e não justificava a perda de performance |
| Face culling antes de greedy meshing | corretude e debug primeiro; otimização entra com perfil |
| Índice superficial derivado no modelo | evita varredura vertical por pixel no HUD sem criar segunda fonte de dados; reconstruível a partir dos arrays persistidos |
| Buffer ARGB puro + uma `DynamicTexture` | mantém geração de pixels fora da render thread e limita objetos GPU a um, independentemente do tamanho do mapa |
| Rotação na pose, não nos dados | o cache permanece norte-acima e não precisa ser refeito quando o jogador gira |
| Cor por face em vez de atlas | escopo escolhido para a Fase 6: reusa o resolutor do HUD e evita ciclo de vida de texturas GPU; texturas ficam para fase futura |
| Malha por chunk com culling de vizinhos | remove faces ocultas sem objeto por bloco e mantém a vizinhança imediata para continuidade entre chunks |
| Revisão espacial + LRU assíncrono | uma mudança invalida somente o chunk e vizinhos de culling; jobs obsoletos são descartados e memória/quantidade têm limites |
| LOD horizontal adaptativo | reduz superfícies extensas sem preencher ar/UNKNOWN nem deformar cavernas; mistura força subdivisão |
| Reconciliação somente de voxels conhecidos | mudanças do cliente aparecem sem transformar chunk carregado em terreno descoberto |
| `identity()` na pilha model-view | telas renderizam com uma translação GUI (~+9990 Z); sem isso a câmera 3D ficaria deslocada |
