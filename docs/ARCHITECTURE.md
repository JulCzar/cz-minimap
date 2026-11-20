# Arquitetura proposta

> **Estado:** as fases 1 a 3 implementaram `core`, `cartography`, `world`, `discovery` e a composição cliente. Este documento descreve a implementação real; os demais pacotes da tabela continuam como proposta para fases futuras.
> As regras normativas estão em [`AGENTS.md`](../AGENTS.md). Este documento traduz essas regras em fronteiras concretas de código.

## 1. Princípio central

O **modelo cartográfico persistente** é a única fonte da verdade. Minimap, malhas 3D, LOD, câmera, camadas e cutaway são projeções descartáveis, reconstruíveis a partir dele. Nenhuma projeção alimenta o modelo nem serve como fonte independente de dados de mundo.

Consequência prática: todo dado de mundo que entra no modelo passa por um único caminho de escrita (coleta → descoberta → aplicação), e todo consumidor lê por snapshots imutáveis com versão.

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
- `world` e `discovery` são os **únicos produtores** de mudanças no modelo. As leituras de mundo vivo acontecem apenas na game thread: `ChunkDataCollector` acessa chunks já carregados e produz `VoxelSample`s imutáveis; `DiscoveryEngine` agrega essas observações em `WorldDataSnapshot` e as entrega à `DiscoveryPolicy`, que é pura e não conhece Minecraft. Nenhum snapshot carrega referência viva de `Level`, `ChunkAccess` ou `BlockState`.
- `storage` serializa o modelo, mas a renderização nunca lê arquivos nem o estado mutável do storage.
- `terrain`, `layers`, `cutaway` e `lod` derivam dados; suas caches são chaveadas por versão do snapshot e descartadas quando a versão muda.
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
| `com.czminimap.world` | `ChunkDataCollector`, `VoxelSample`, `WorldDataSnapshot`, `BlockPositionKey` | 3 |
| `com.czminimap.discovery` | `DiscoveryPolicy`, `ExplorationTracker`, `DiscoveryEngine`, `DiscoverySettings`, `DiscoveryResult` | 3 |
| `com.czminimap.storage` | formato de região, escrita atômica, fila assíncrona, migração | 4 |
| `com.czminimap.terrain` | resolução de material, geração de malha, agregação por região | 6 |
| `com.czminimap.layers` | intervalos verticais e modos de visualização | 7 |
| `com.czminimap.cutaway` | recorte horizontal/vertical e resolução de clip | 7 |
| `com.czminimap.lod` | níveis, seleção por distância, cache e limites | 8 |
| `com.czminimap.rendering.minimap` | tiles 2D e desenho no HUD | 5 |
| `com.czminimap.rendering.worldmap` | câmera orbital, malhas, recursos GPU | 6 |
| `com.czminimap.gui` | tela do mapa e controles | 6 |

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

`CartographicSnapshot` carrega `version` e um mapa imutável de regiões; os limites de dimensão estão embutidos em cada `DimensionKey` das chaves. Paleta, chunks, seções e arrays são copiados na construção, e a API só expõe leitura. Toda cache de apresentação será chaveada por `(regionKey, version, modo/camada/LOD)`. Quando o modelo muda, a versão incrementa e caches antigas ficam elegíveis para evicção; nada é mutado "in place" por consumidores.

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
  -> [Fase 4] storage recebe job assíncrono deduplicado
  -> versão incrementada -> [Fases 5/6] invalida caches de apresentação
```

Mudanças de bloco recebidas pelo cliente ainda não alimentam esse fluxo (Fase 9).

### Leitura (render)

```text
CartographicSnapshot(version)
  -> worker: gerar tile 2D / malha / LOD (somente dados imutáveis)
  -> cache por chave (região, versão, modo)
  -> render thread: somente upload/draw do recurso já pronto
```

A HUD e o mapa 3D consomem o mesmo snapshot; nenhum dos dois escaneia o mundo.

## 6. Threading

| Onde | Permitido | Proibido |
|---|---|---|
| Game thread | acessar `Level`/chunks, tracking do jogador, aplicar descoberta, handlers de evento, mudanças de config | I/O de disco, geração de malha, upload GPU |
| Render thread | desenhar e fazer upload/destroy de recursos GPU | I/O, acesso a mundo mutável, geração de geometria |
| Workers limitados | serialização/compressão, construção de malha a partir de snapshot, LOD, indexação | acessar mundo vivo, tocar GPU |

Fila com limites e deduplicação por chave de região. Cada job carrega um `generationToken`; resultados de gerações obsoletas são descartados. Prioridade: regiões visíveis no mapa 3D → próximas ao jogador → visíveis na HUD → demais.

## 7. Persistência proposta (Fase 4 — não implementada)

> Caminho e codec abaixo são propostas em conflito com o plano (`.minecraft/czminimap` + LZ4); serão unificados ao implementar a Fase 4.

- Local: `config/czminimap/data/<worldHash>/dimensions/<namespace>/<path>/regions/r.<x>.<z>.bin` (nomes de dimensão escapados para filesystem).
- Arquivo de região: cabeçalho com magic `CZM1`, versão de schema, hash de mundo, `DimensionKey`, coordenadas, flags, tamanho e checksum; payload com índice de seções + paleta + bitsets + metadados.
- Compressão: `java.util.zip.Deflater`/`Inflater` como primeira opção (zero dependências); LZ4 apenas se houver justificativa de performance.
- Escrita: arquivo temporário no mesmo diretório → flush → `Files.move` com `ATOMIC_MOVE` e `REPLACE_EXISTING`, com fallback e `.bak` para recuperação.
- Dirty tracking + flush periódico, na desconexão, na troca de dimensão e no shutdown. Fila assíncrona limitada; nunca na render thread.
- Arquivo corrompido: log, quarentena (`.corrupt`) e região vazia em memória — nunca crash.
- Migração: cadeia de `StorageMigration` por versão; versão mais nova que a suportada = falha controlada com mensagem clara.

## 8. Descoberta

- **Superfície (implementado):** cursor espiral sobre colunas no raio configurado; `WORLD_SURFACE` fornece o topo, amostrado junto com até dois blocos abaixo. Topo = `EXPOSED_SURFACE`; inferiores expostos lateralmente = `SOLID`. Sem raycast.
- **Subterrâneo (implementado):** BFS limitado a partir do bloco do jogador e seus 6 vizinhos. Ar conectado abaixo do heightmap = `UNDERGROUND_SPACE`; acima dele = `KNOWN_AIR` sem expansão. Sólidos encontrados na fronteira = `SOLID`. Blocos internos de montanha não são revelados por estar em chunk carregado.
- **Orçamento:** `max_samples_per_tick` divide-se entre ar e superfície; fila de ar limitada a 16.384 posições; rebase do cursor após 8 blocos de deslocamento, mudança de raio, dimensão ou mundo.
- **Estado:** `UNKNOWN` nunca é escrito no modelo; troca de dimensão preserva dados, troca de mundo troca o mapa. A persistência (Fase 4) ainda não existe, então o estado descoberto é volátil entre sessões.
- Ainda não implementado: detecção de mudanças de bloco, visibilidade direcional (olhar) fora da conectividade de ar e medição de frame time em mundo grande.

## 9. Representação 3D, camadas, cutaway e LOD

- Malhas por região com remoção de faces ocultas; uma entidade de render por região, nunca por bloco. Greedy meshing é otimização posterior à corretude.
- Material resolver converte voxel → UV do atlas + tint de biome + comportamento de transparência, sem acessar mundo vivo. Fallback consistente para modelos não suportados e texturas ausentes; invalidação em resource reload.
- `LayerRange(minYInclusive, maxYExclusive)` com intervalo padrão de 16 blocos, ancorado no `minY` real da dimensão. Modos: intervalo selecionado, cumulativo (direção configurável), terreno completo e cutaway. **Y nunca é reposicionado.**
- Cutaway filtra na geração/render da malha, preservando `UNKNOWN` e `KNOWN_AIR`; muda apenas dados de apresentação, nunca o modelo.
- LOD 0..3: blocos texturizados → grupos agregados → cor dominante → silhueta regional. Seleção por distância/tamanho projetado; células com ar conhecido, desconhecido ou mistura relevante caem para LOD mais fino. Cache LRU com orçamento de memória e jobs deduplicados.

## 10. Configuração e input

- Config apenas `ModConfig.Type.CLIENT`, com `ModConfigSpec` e validação de intervalos. Apresentação separada de dados persistentes; mudar config nunca apaga mapa. Arquivos distintos: `czminimap-hud-client.toml` e `czminimap-map-client.toml` (o registro de dois specs CLIENT com o mesmo nome gera conflito no FML).
- `KeyMapping`s registrados via `RegisterKeyMappingsEvent` no barramento do mod, somente em classe `DIST.CLIENT`; checagem em `ClientTickEvent.Post` com `consumeClick()`. Categoria própria `key.categories.czminimap` e traduções `en_us`/`pt_br`. As classes usam `@EventBusSubscriber(value = Dist.CLIENT)` sem o atributo `bus`, deprecado e removido no NeoForge 1.21.1 — o barramento é inferido pelo tipo de evento.
- Ações: abrir/fechar mapa, alternar HUD, zoom da HUD, reset de câmera, retornar ao jogador e navegação de camadas. Defaults são provisórios e serão validados contra conflitos na Fase 1.

## 11. Decisões de design e justificativas

| Decisão | Justificativa |
|---|---|
| Fonte única de verdade + snapshots versionados | impede divergência entre HUD e mapa 3D e permite workers sem locks |
| Paleta + bitsets por região | escala melhor que um objeto por bloco e preserva `UNKNOWN` vs `KNOWN_AIR` |
| Campo temporal separado do estado | evita combinações contraditórias de flags de descoberta |
| Um único ponto de escrita no modelo | facilita a invalidação, a persistência e a auditoria do que foi descoberto |
| Entrypoint comum sem imports de cliente | mantém classes de cliente fora do caminho do servidor dedicado |
| Compressão JDK primeiro | evita dependência de terceiros sem necessidade técnica |
| Face culling antes de greedy meshing | corretude e debug primeiro; otimização entra com perfil |
