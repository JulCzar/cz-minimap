# Plano de implementação

> **Estado:** fases 1, 2, 3, 4, 5, 6 e 7 concluídas. A execução segue as fases do `AGENTS.md` §18, uma por vez.
> A execução deve seguir as fases do [`AGENTS.md`](../AGENTS.md) §18, uma por vez, com validação e atualização de documentação ao final de cada uma.

## 0. Versões e ambiente verificados (19/11/2026)

| Item | Valor verificado | Fonte |
|---|---|---|
| JDK | 21.0.13 instalado | `java -version` no ambiente |
| Minecraft | 1.21.1 | exigência do projeto |
| NeoForge | `21.1.267` (POM aponta `neoform 1.21.1-20240808.144430`) | Maven NeoForge |
| MDK | `MDK-1.21-ModDevGradle`, branch `main` | GitHub NeoForgeMDKs |
| Plugin | `net.neoforged.moddev` 2.0.148 | `build.gradle` do MDK |
| Gradle | wrapper 9.2.1 (`-bin`) | `gradle-wrapper.properties` do MDK |
| Parchment | MC 1.21 / mappings 2024.11.10 | `gradle.properties` do MDK |
| Foojay resolver | 1.0.0 | `settings.gradle` do MDK |

**Atenção ao ambiente local:** `asdf` não está disponível como comando e o Gradle de sistema é 4.4.1, incompatível com o MDK. Use sempre `./gradlew`. O Java 21 do sistema (`/usr/lib/jvm/java-21-openjdk-amd64`) é um **JRE sem `javac`** e faz o NeoForm falhar com `release version 21 not supported`; aponte `JAVA_HOME` para um JDK 21 completo, por exemplo `/home/julczar/.asdf/installs/java/adoptopenjdk-21.0.11+10.0.LTS`.

Fontes consultadas: documentação oficial NeoForge 1.21.1 (Getting Started, Versioning, Configuration, Key Mappings) e repositório do MDK.

## 1. Regras de entrega por fase

1. Implementar apenas o escopo da fase; sem stubs, placeholders ou funcionalidade fingida.
2. Ao final: `./gradlew build`, validação específica da fase e revisão do diff.
3. Atualizar `README.md`, `docs/ARCHITECTURE.md` e este plano com o estado real.
4. Nenhuma fase de interface deve introduzir varredura de mundo ou segunda fonte de dados.

| Fase | Status |
|---|---|
| 1 — Fundação | Concluída em 19/11/2026 |
| 2 — Modelo cartográfico | Concluída em 19/11/2026 |
| 3 — Descoberta | Concluída em 20/11/2026 |
| 4 — Persistência | Concluída em 20/11/2026 |
| 5 — Minimap 2D | Concluída em 20/11/2026 |
| 6 — Renderizador 3D inicial | Concluída em 20/11/2026 |
| 7 — Camadas verticais e cutaway | Concluída em 20/11/2026 |
| 8 a 10 | Não iniciadas |

## 2. Fases

### Fase 1 — Fundação do projeto

**Status: concluída.**

**Tarefas**

1. Importar a estrutura do MDK (sem `ExampleMod`): `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew*`, `gradle/wrapper`, `.gitignore` e `src/main/templates/META-INF/neoforge.mods.toml`.
2. Ajustar `gradle.properties`: `minecraft_version=1.21.1`, `neo_version=21.1.267`, `loader_version_range=[1,)`, `mod_id=czminimap`, `mod_name=CZ Minimap`, `mod_group_id=com.czminimap`, `mod_version=0.1.0`.
3. Criar `CzMinimap` (entrypoint comum, somente constantes) e `CzMinimapClient` (`@Mod(dist = Dist.CLIENT)` + eventos de cliente).
4. Registrar os `KeyMapping`s da Fase 1 com `RegisterKeyMappingsEvent` e tratá-los em `ClientTickEvent.Post`; categoria `key.categories.czminimap`.
5. Criar esqueleto de `ClientConfig`/`MapConfig` com `ModConfigSpec`, registrado como `ModConfig.Type.CLIENT`.
6. Adicionar `en_us.json` e `pt_br.json` com nomes de keys e categoria.

**Artefatos:** raiz do projeto Gradle + `src/main/java/com/czminimap/...` + templates de metadata.

**Critérios de aceite:** o mod compila e inicia no Minecraft 1.21.1; aparece na lista de mods; os atalhos aparecem em Controles; nenhuma classe de cliente é inicializada em servidor dedicado.

**Validação executada:** `./gradlew build` e `./gradlew runData` passaram com JDK 21 completo. O log de `runData` confirma `CZ Minimap 0.1.0 (czminimap)` na lista de mods, os dois arquivos de config com nomes distintos e as duas classes `@EventBusSubscriber` registradas nos barramentos corretos (mod bus para `RegisterKeyMappingsEvent`, game bus para `ClientTickEvent.Post`). O JAR contém as classes, `neoforge.mods.toml` expandido e os dois arquivos de idioma.

`./gradlew runClient` também iniciou o cliente até o menu: mod na lista, configs monitoradas, reload com `mod/czminimap`, 1290 receitas e 1399 advancements carregados. O comando foi encerrado por `timeout` (`exit=124`); os erros de `Failed to open OpenAL device` e `libflite.so` são limitações de áudio/narrador deste ambiente, não do mod. A verificação visual de Controles/HUD não foi possível sem interface gráfica interativa.

**Riscos:** versão do NeoForge; conflito de defaults de teclas; isolamento cliente/servidor.

**Correções feitas durante a validação:** import de `EventBusSubscriber` (`net.neoforged.fml.common`) e de `ClientTickEvent` (`net.neoforged.neoforge.client.event`); atributo `bus` removido por depreciação no NeoForge 1.21.1; nomes de arquivo de config diferenciados para evitar conflito de `czminimap-client.toml`.

---

### Fase 2 — Modelo cartográfico

**Status: concluída.**

**Tarefas:** identidades (`WorldKey`, `WorldIdentity`, `DimensionKey`, `RegionKey`); paleta por região e seções esparsas de 16³; `DiscoveryState` + `lastObservedTick` por seção; `CartographicMap` com único ponto de escrita e `CartographicSnapshot` versionado; limites de altura por dimensão; resolver de identidade no cliente; config de modo de identidade; testes unitários de transições, imutabilidade e identidade.

**Critérios de aceite:** representa `UNKNOWN` distinto de `KNOWN_AIR`; respeita alturas diferentes por dimensão; não guarda referências vivas de mundo; snapshots não podem ser mutados por consumidores.

**Validação executada:** `./gradlew test build` passou (8 testes: 5 de modelo, 3 de identidade). `./gradlew runData` e uma inicialização curta de `runClient` confirmaram o mod carregando e o novo `world_identity_mode = "NAME_AND_HASH"` no TOML. O classpath de teste precisou receber explicitamente o classpath de main, porque o ModDevGradle anexa Minecraft/NeoForge diretamente ao `compileClasspath`.

**Riscos:** granularidade do `lastObservedTick` por seção pode ser insuficiente para envelhecimento visual fino; trocar o modo de identidade cria outro diretório de dados (sem migração automática).

---

### Fase 3 — Descoberta

**Status: concluída.**

**Tarefas:** `MapManager` como dono do mapa da sessão; `ChunkDataCollector` para leitura somente de chunks já carregados (`hasChunk` + `getChunk(..., ChunkStatus.FULL, false)`); `WorldDataSnapshot`/`VoxelSample`/`BlockPositionKey`; `DiscoveryPolicy` pura; `ExplorationTracker` com cursor espiral e fronteira de ar limitada; `DiscoveryEngine` no tick do cliente; integração via `ClientMapContext` em `ClientTickEvent.Post`.

**Algoritmo adotado**

- **Superfície:** para cada coluna do cursor espiral (raio `discovery.surface_radius`), o heightmap `WORLD_SURFACE` fornece o topo; amostra-se o topo e até dois blocos abaixo. O topo vira `EXPOSED_SURFACE`; os inferiores, `SOLID` somente se expostos lateralmente a ar.
- **Subterrâneo:** a fronteira começa no bloco do jogador e seus 6 vizinhos. Ar é expandido por conectividade de faces, dentro de `discovery.underground_radius` e do intervalo de altura real da dimensão. Ar abaixo do heightmap vira `UNDERGROUND_SPACE`; ar acima vira `KNOWN_AIR` sem expansão. Sólidos encontrados na fronteira viram `SOLID`. Blocos internos de montanha não são revelados.
- **Orçamento:** `discovery.max_samples_per_tick` é dividido entre ar e superfície enquanto a varredura de superfície não termina. A fila de ar tem teto de 16.384 posições; a varredura se reinicia quando o jogador se move mais de 8 blocos ou muda raio/dimensão/mundo.
- A troca de mundo substitui o mapa em memória (`MapManager.selectWorld`); a troca de dimensão preserva os dados (a dimensão faz parte de `RegionKey`) e reinicia apenas as filas.

**Critérios de aceite:** revelação progressiva e parcelada; nenhum carregamento forçado de chunk; `UNKNOWN` permanece distinto de `KNOWN_AIR`; cavernas conectadas são reveladas sem expor sólidos arbitrários; dimensões com alturas diferentes são respeitadas.

**Validação executada:** `./gradlew test build` passou com **16 testes JUnit 5** (modelo, identidade, política, cursor espiral, fronteira limitada, chave de posição, dono do mapa e lookup pontual). `./gradlew runData` passou e `./gradlew runClient` alcançou o menu sem erros do mod; os erros de áudio/narrador (`OpenAL`, `libflite.so`) permanecem como limitação do ambiente.

**Riscos e limitações conhecidas:** a varredura espiral recomeça quando o jogador se desloca mais de 8 blocos, então anéis externos só são concluídos quando ele para; ainda não há detecção de mudanças de bloco, persistência nem visualização, logo o estado descoberto existe apenas em memória e não é diretamente observável no jogo. Perfil de frame time em mundo grande não foi medido.

---

### Fase 4 — Persistência

**Status: concluída.**

**Tarefas:** serialização de região v1; isolamento por mundo/dimensão; dirty tracking; fila assíncrona limitada e deduplicada; escrita atômica com backup; quarentena de arquivo corrompido; recuperação de escrita interrompida; flush em desconexão, troca de mundo e shutdown.

**Critérios de aceite:** dados sobrevivem a reinício; mundos nunca compartilham dados; arquivo corrompido não derruba o jogo; nenhum I/O na render thread.

**Validação executada:** 28 testes JUnit 5 passando (incluindo round-trip `CartographicMap` → `RegionData` → codec → `MapStorage` → novo `CartographicMap`, corrupção, recuperação via `.tmp`, isolamento entre worlds e versão futura rejeitada). `./gradlew build`, `./gradlew runData` e `./gradlew runClient` curto passaram; o JAR contém `lz4-java-1.8.0.jar` em `META-INF/jarjar/`. A validação em jogo real (entrar em mundo, explorar, sair e reabrir) não foi feita neste ambiente por falta de interface gráfica interativa; os ciclos foram cobertos por testes de integração do storage.

**Riscos:** a cadeia de migração não existe porque ainda só existe o schema v1; versão futura falha de forma controlada. A identidade depende do modo de config e mudá-lo cria outro diretório (sem migração), conforme documentado.

---

### Fase 5 — Minimap 2D

**Status: concluída.**

**Tarefas:** índice derivado de superfície no modelo cartográfico; `MinimapColorResolver`, `MinimapImage` e `MinimapLayout` como classes puras; `MinimapRenderer` com `DynamicTexture` única; integração em `ClientTickEvent.Post` e `RenderGuiEvent.Post`; configuração de posição customizada, opacidade do painel, bússola e coordenadas; testes unitários e de integração do índice.

**Decisões adotadas**

- O HUD não faz varredura vertical: `CartographicMap` mantém, por mundo/dimensão/coluna, o bloco sólido mais alto descoberto. O índice é derivado, não persistido e reconstruído em `loadRegion`.
- O buffer é sempre norte-acima; a rotação para cabeçalho-acima acontece na `PoseStack`, então girar o jogador não invalida o cache de pixels.
- `MinimapRenderer.tick` (game thread) só chama `snapshot()` quando `map.version()` ou os parâmetros de visualização mudam, com throttle de 100 ms. `render` (render thread) apenas converte ARGB→ABGR, faz upload e desenha.
- Máscara circular e moldura são escritas no buffer com alfa zero fora do círculo; não há shader ou scissor circular.
- O HUD some quando uma tela está aberta, quando o HUD é desligado por config/atalho ou quando o mapa 3D é aberto.

**Critérios de aceite:** render contínuo sem scan de mundo; funciona em diferentes escalas de GUI e tamanhos de janela; independente da câmera/cutaway do mapa 3D.

**Validação executada:** 39 testes JUnit 5 passando (incluindo índice superficial derivado, reconstrução a partir de dados persistidos, cores determinísticas, máscara circular, mapeamento de zoom fracionário e oito âncoras de posição). `./gradlew build` e `./gradlew runData` passaram. O cliente foi iniciado para verificação do HUD; a validação visual completa de cada posição/forma/orientação depende de sessão interativa e permanece como limite deste ambiente.

**Riscos:** o upload da textura reprocessa até 512×512 pixels quando um parâmetro muda; a frequência é limitada pelo throttle e pelo tamanho máximo configurável. A correspondência exata de matizes com o atlas de blocos ainda será refinada na Fase 6, quando existir `MaterialResolver`.

---

### Fase 6 — Renderizador 3D inicial

**Status: concluída.**

**Tarefas:** câmera orbital independente; `CartographicSnapshot.forEachSolid` para leitura imutável de voxels; malhas por chunk com remoção de faces ocultas (incluindo fronteira entre chunks); cor por face via `MinimapColorResolver` com sombreamento fixo; cache LRU de malhas por versão do snapshot com orçamento de reconstrução por tick; `RenderType` próprio com `POSITION_COLOR`/QUADS; marcador do jogador; tela cheia com rotação/pan/zoom, reset, retorno ao jogador e painel mínimo; configuração `worldmap` e traduções.

**Decisões adotadas**

- Aparência do mapa 3D é **apenas cor por face** (`WorldMapMaterialResolver`), decidida explicitamente com o usuário. Não há atlas, `BlockState` vivo, tint dinâmico nem texturas nesta fase; `MinimapColorResolver` continua sendo a única origem de cor tanto no HUD quanto no 3D.
- Escopo de UI é **câmera + painel mínimo**, decidido com o usuário. Sem camadas, cutaway ou seletor de intervalo (Fase 7).
- Geometria é por **chunk**, não por região: o custo de reconstrução é menor e o raio de retenção é configurável. A vizinhança 3×3 de chunks é amostrada só para culling de faces na borda.
- `UNKNOWN`, `KNOWN_AIR` e `UNDERGROUND_SPACE` não viram geometria. Face só é removida quando o vizinho é `SOLID`/`EXPOSED_SURFACE` conhecido — o renderizador nunca infere terreno a partir de dados ausentes.
- `RenderType.create("czminimap_terrain", POSITION_COLOR, QUADS, ...)` com `NO_CULL` e `LEQUAL_DEPTH_TEST`, usando `GameRenderer::getPositionColorShader`. Nenhum recurso GPU persistente é criado: os quads vão pelo `BufferSource` do `GuiGraphics`.
- `Screen.render` roda com translação GUI na pilha model-view (~+9990 Z), então `WorldMapRenderer.render` aplica `identity()` após o push antes de multiplicar pela matriz da câmera, e restaura projeção/model-view ao final.
- `WorldMapRenderer.tick` só avança a versão/base de cache quando **todos** os chunks do raio foram reconstruídos; se `mesh_budget` não cobre a demanda, o trabalho continua no tick seguinte.
- `M` abre/fecha a tela; `mapOpen` esconde o HUD; `ESC` chama `onClose` e sincroniza `ClientUiState.setMapOpen(false)`.

**Critérios de aceite:** modelo voxel rotacionável via câmera independente, nenhum objeto de render por bloco, malhas invalidadas por versão do snapshot e sem fonte de dados própria. Texturas reconhecíveis, LOD, cutaway e camadas ficaram explicitamente fora desta passada e são critérios das Fases 7–8.

**Validação executada:** 61 testes JUnit 5 passando (22 novos: câmera/transformações, culling de faces e fronteira de chunk, cor por face, cache LRU/versão/raio e `forEachSolid`). `./gradlew clean build`, `./gradlew test`, `./gradlew runData` e `./gradlew runClient` passaram; o cliente entrou como `Dev` em `(-79.97, 73.0, 61.28)` sem exceções do mod. A abertura da tela e a inspeção visual de terreno/cavernas não puderam ser automatizadas neste ambiente (sem dispositivo de entrada/screenshot funcional); a validação visual interativa permanece como limite conhecido.

**Riscos:** api de renderização customizada em 1.21.1 foi confirmada por `javap` nas fontes do NeoForge 21.1.267, mas o caminho de desenho da tela não foi exercido por input automatizado. A geração de malha roda na game thread com orçamento; perfis em área extensa ainda não foram medidos. Texturas, transparência e resource reload ficam para a fase de material completo.

---

### Fase 7 — Camadas verticais e cutaway

**Status: concluída.**

**Tarefas:** `LayerRange` e resolução ancorada no `minY` da dimensão; modos selecionado/cumulativo/completo/cutaway; recorte horizontal e vertical; controles na tela; invalidação apenas de dados de apresentação.

**Decisões adotadas**

- `LayerResolver` gera intervalos half-open ancorados em `DimensionKey.minY()` e trunca o último em `maxYExclusive`; a seleção é ajustada ao intervalo que contém o jogador ao entrar ou trocar de dimensão.
- `LayerVisibility` é um filtro imutável. O modo selecionado guarda um intervalo; o cumulativo guarda a lista de intervalos desde a seleção para cima ou para baixo; completo e cutaway cobrem toda a dimensão. As coordenadas Y dos voxels nunca são deslocadas.
- O cutaway horizontal mantém `y < maxYExclusive` e avança em intervalos verticais. O cutaway vertical usa um plano constante em `X` ou `Z`, com lado negativo (`<`) ou positivo (`>=`) configurável; os dois planos podem ser combinados.
- `TerrainMeshBuilder` aplica o mesmo filtro ao voxel emitido e aos sólidos usados no culling. Assim, um vizinho conhecido mas ocultado não remove a face da superfície de corte; `UNKNOWN`, `KNOWN_AIR` e `UNDERGROUND_SPACE` continuam fora da geometria.
- `WorldMapMeshCache` valida `snapshot.version()` **e** igualdade estrutural de `LayerVisibility`; durante reconstrução progressiva, o renderer desenha somente malhas da visualização atual. Alterar modo/camada/corte não toca `CartographicMap` nem sua versão.
- A retenção e a seleção usam o mesmo quadrado de chunks. Se `max_cached_chunks` não comportar o raio configurado, o raio efetivo cai para o maior quadrado completo que cabe; mudanças desse raio participam da invalidação, evitando rebuild infinito ou viewport stale.
- `WorldMapScreen` oferece widgets para modo, direção cumulativa, faixa anterior/próxima, cortes horizontal/vertical, eixo, lado e deslocamento de 16 blocos. Widgets têm precedência sobre drag/scroll da câmera.
- `layers.default_mode`, `layers.interval_size` e `layers.cumulative_direction` ficam em configuração de apresentação; nenhum deles é persistido junto aos dados cartográficos.

**Critérios de aceite:** intervalos `[min, max)` com Y real preservado; cutaway nunca revela `UNKNOWN`; múltiplos intervalos; limites por dimensão.

**Validação executada:** 79 testes JUnit 5 passando (18 novos para intervalos half-open, Y negativo, último intervalo parcial, cumulativo/múltiplos intervalos, planos de corte, superfícies de corte entre chunks, invalidação do cache sem mudar a versão cartográfica e ajuste do raio à capacidade do cache). `./gradlew clean build`, `./gradlew runData` e uma inicialização curta de `./gradlew runClient` passaram com JDK 21 e NeoForge 21.1.267; o cliente chegou ao reload de recursos/menu sem exceções do mod e foi encerrado intencionalmente. A inspeção visual interativa dos controles em mundos/dimensões diferentes ainda depende de sessão manual.

**Riscos:** o risco original de reconstrução na game thread foi removido na Fase 8; alternar filtros ainda publica chunks progressivamente a partir dos workers. O perfil de frame time e a validação visual em Overworld/Nether/End permanecem pendentes de sessão interativa.

---

### Fase 8 — LOD híbrido

**Status: concluída.**

**Tarefas:** níveis 0..3; seleção por distância/tamanho projetado; agregação com simplificação de material; construção assíncrona a partir de snapshots; cache LRU com orçamento; continuidade entre regiões; compatibilidade com camadas/cutaway.

**Critérios de aceite:** grandes áreas inspecionáveis sem detalhe integral; transições sem buracos; cavernas e fronteiras de desconhecido preservadas; rebuild apenas da região afetada.

**Decisões adotadas**

- `LodLevel` usa escalas horizontais 1/2/4/8. A seleção estima pixels por bloco a partir de distância, FOV e altura da viewport, limitada por `rendering.lod_quality`.
- A agregação acontece em fatias de um bloco de altura: uma célula maior só é emitida quando toda a área é sólida, conhecida e visível. Ar, `UNKNOWN`, buraco, cutaway ou mistura geométrica força subdivisão recursiva; assim o LOD não preenche cavernas nem move Y.
- O material de célula é o `VoxelAppearance` dominante; LOD 0 preserva a resolução de bloco. A aparência continua cor por face, decisão herdada da Fase 6.
- `WorldMapMeshCache` usa workers daemon limitados, fila deduplicada por geração/chunk/revisão/LOD e publicação limitada por `mesh_budget`. Entradas são LRU com limites por `max_cached_chunks` e `mesh_memory_mb`.
- Durante transição, a malha anterior continua visível enquanto tiver a mesma revisão e `LayerVisibility`; filtros obsoletos nunca são usados como fallback.
- `CartographicSnapshot.meshRevision` inclui a vizinhança 3×3 de culling. Alteração distante reutiliza a malha; alteração no chunk ou vizinho a invalida.

**Validação executada:** 95 testes JUnit 5 passando, incluindo níveis e seleção projetada, redução de geometria em superfície agregável, fallback diante de `UNKNOWN`, job assíncrono, continuidade de transição, invalidação local/vizinha e compatibilidade com camada/cutaway. `./gradlew clean build`, `./gradlew runData` e uma inicialização curta de `./gradlew runClient` passaram; o cliente concluiu reload de recursos e chegou ao menu sem exceções do mod.

**Riscos:** não houve perfil interativo de frame time/memória em mundo grande. A agregação é horizontal e conservadora; terrenos irregulares preservam corretude, mas podem cair para LOD fino. Atlas/texturas continuam fora desta fase.

---

### Fase 9 — Sincronização de mudanças

**Status: concluída.**

**Tarefas:** captura de mudanças de bloco recebidas pelo cliente; atualização do modelo descoberto; invalidação de tiles, malhas e LOD; verificação em multiplayer.

**Critérios de aceite:** mudanças recebidas aparecem nas regiões descobertas; nenhum rebuild global; nada fora da área conhecida é inferido.

**Decisões adotadas**

- O NeoForge 21.1.267 não oferece um evento genérico de atualização de bloco recebido no cliente. `BlockUpdateSynchronizer` faz reconciliação incremental diretamente em chunks já carregados, funcionando também quando o servidor multiplayer não possui o mod.
- Somente posições já descobertas são amostradas. `UNKNOWN` nunca é promovido; sólido/ar/aparência podem mudar e continuam passando pelo único ponto de escrita `CartographicMap.setVoxel`.
- A varredura alterna chunks por distância e processa no máximo 64 posições de cada chunk por passagem. As listas conhecidas são cacheadas até a revisão do chunk mudar; raio e orçamento são configuráveis em `synchronization`.
- Remover o topo conhecido reconstrói o índice superficial. Dirty tracking e timestamps persistem a mudança pelo formato `CZM1`, sem migração de schema.
- O HUD usa a maior revisão dos chunks visíveis, ignorando alterações distantes. Malha/LOD usam revisão do chunk + vizinhos de culling, evitando rebuild global.

**Validação executada:** testes cobrem bloqueio de `UNKNOWN`, remoção para ar, colocação em ar conhecido, atualização de aparência/índice superficial, revisão de área do HUD e invalidação espacial de malha. A suíte completa, o build limpo, `runData` e o carregamento curto do cliente passaram.

**Riscos:** a latência depende de `scan_radius_chunks` e `max_samples_per_tick`. A edição manual em servidor multiplayer real ainda precisa de sessão externa; não há acesso servidor nem garantia de atualização fora dos chunks carregados/raio de reconciliação.

---

### Fase 10 — UX e otimização

**Tarefas:** refino de controles e feedback de carregamento; limites de memória/cache; perfil de main/render thread; testes de mundo grande; documentação final.

**Critérios de aceite:** estável em sessões longas; sem picos inexplicáveis de frame ou crescimento de memória; documentação alinhada à implementação.

**Validação:** medições de frame time, memória, I/O e filas; replay dos cenários das fases anteriores.

## 3. Decisões

### Adotadas na Fase 1

| Decisão | Valor adotado |
|---|---|
| Patch do NeoForge | `21.1.267` fixado para reprodutibilidade |
| Intervalo de Minecraft | `[1.21.1,1.21.2)` |
| Isolamento cliente/servidor | entrypoint comum + handlers cliente via `@EventBusSubscriber(value = Dist.CLIENT)`; o NeoForge 1.21.1 infere o barramento pelo tipo de evento (atributo `bus` está deprecated) |
| Defaults de teclas | provisórios: M = mapa, B = HUD, `=`/`-` = zoom, R = reset, P = jogador, `[`/`]` = camadas |
| Idiomas | `en_us.json` e `pt_br.json` |

### Adotadas na Fase 2

| Decisão | Valor adotado |
|---|---|
| Testes | JUnit 5 (`junit-bom` 5.11.4) com `useJUnitPlatform()`; classpath de teste estende o classpath de main para enxergar Minecraft/NeoForge |
| Identidade de mundo | configurável via `storage.world_identity_mode`; padrão `NAME_AND_HASH` = nome/pasta sanitizado + hash SHA-256 truncado (12 hex) de identidade normalizada; alternativas `HASH_ONLY` e `NAME_ONLY`. Endereço do servidor e seed nunca são gravados em claro |
| Armazenamento (Fase 4) | diretório `<gameDir>/czminimap` com compressão LZ4 (jarJar), um arquivo por região 512×512 |

### Adotadas na Fase 3

| Decisão | Valor adotado |
|---|---|
| Leitura de mundo | somente chunks já disponíveis no cliente; `getChunk(cx, cz, ChunkStatus.FULL, false)`, nunca carregamento forçado |
| Superfície | heightmap `WORLD_SURFACE` + exposição do topo e de até dois blocos abaixo; sem raycast |
| Subterrâneo | ar conectado a partir do jogador + sólidos adjacentes; classificação por altura relativa ao heightmap |
| Agendamento | cursor espiral determinístico, fronteira BFS limitada, orçamento compartilhado por tick e rebase após 8 blocos |
| Aparência de ar | `KNOWN_AIR`/`UNDERGROUND_SPACE` não entram na paleta (`appearance = null`) |
| Dono do estado | `MapManager` troca o `CartographicMap` ao mudar de mundo; dimensão faz parte da chave de região |

### Adotadas na Fase 4

| Decisão | Valor adotado |
|---|---|
| Local dos dados | `<gameDir>/czminimap/<storageId>/dimensions/<namespace>/<path>/regions/r.<x>.<z>.bin` |
| Formato | `CZM1`: magic + schemaVersion + identidade + limites + coordenadas + tamanhos + CRC32; payload LZ4 com paleta, chunks, seções e `lastObservedTick` |
| Compressão | LZ4 (`org.lz4:lz4-java:1.8.0`), declarado e embutido via `jarJar` |
| Granularidade | um arquivo por região 512×512, alinhado ao dirty tracking existente |
| Carga | worker lê todos os arquivos do mundo e publica um batch com token; descoberta fica pausada até o game thread aplicar o batch |
| Escrita | `.tmp` → `.bak` → `ATOMIC_MOVE` com fallback; corrompido vai para `.corrupt`; `.tmp`/`.bak` válidos recuperam o principal |
| Mesclagem | por seção, usando `lastObservedTick`; relógio monotônico entre sessões evita dados antigos vencerem descobertas novas |

### Adotadas na Fase 5

| Decisão | Valor adotado |
|---|---|
| Evento de HUD | `RenderGuiEvent.Post` (confirmado no NeoForge 21.1.267) |
| Fonte dos pixels | `CartographicSnapshot.surfaceAt(...)`, nunca `Level`/`ChunkAccess` |
| Cache | versão do snapshot + centro/zoom/tamanho/forma/mundo/dimensão; throttle de 100 ms |
| Recurso GPU | uma `DynamicTexture` RGBA registrada como `czminimap:hud_minimap` |
| Orientação | rotação na `PoseStack`; cache permanece norte-acima |
| Cor | resolutor determinístico por `block id` e `biome id`; `UNKNOWN` = `0xFF181818` |
| Config adicional | `background_opacity`, `custom_x`, `custom_y`, `show_compass`, `show_coordinates` |

### Adotadas na Fase 6

| Decisão | Valor adotado |
|---|---|
| Aparência 3D | apenas cores por face via `MinimapColorResolver` + sombreamento fixo por direção; sem atlas ou texturas (decisão do usuário) |
| UI | câmera orbital + painel mínimo (decisão do usuário); sem camadas/cutaway nesta fase |
| Unidade de malha | chunk, com culling de faces usando vizinhança 3×3 de chunks |
| `RenderType` | `czminimap_terrain`, `POSITION_COLOR`/QUADS, `NO_CULL`, `LEQUAL_DEPTH_TEST`, shader `getPositionColorShader` |
| Matriz de tela | `identity()` na pilha model-view antes de aplicar a view; projeção e pilha restauradas após o desenho |
| Cache de malhas | LRU por chunk + versão do snapshot; `max_cached_chunks` e `mesh_budget` configuráveis; `retainRadius` por `render_radius` |
| Orçamento | versão/base de cache só avança quando todos os chunks do raio são reconstruídos |
| Config adicional | `worldmap.fov`, `render_radius`, `sensitivity`, `max_cached_chunks`, `mesh_budget`, `background_opacity` |

### Adotadas na Fase 7

| Decisão | Valor adotado |
|---|---|
| Intervalos | `[minYInclusive, maxYExclusive)`, ancorados no `minY` real; último intervalo truncado no limite da dimensão |
| Modos | `FULL`, `SELECTED`, `CUMULATIVE` (`UP`/`DOWN`) e `CUTAWAY` |
| Corte horizontal | plano voxel-aligned que mantém `y < maxYExclusive`, movido pela seleção de intervalo |
| Corte vertical | plano world-space em `X` ou `Z`, lado visível `<` ou `>=`, deslocamento de 16 blocos |
| Culling | filtro idêntico para emissão e vizinhos; sólidos ocultos expõem a face do sólido conhecido visível |
| Cache | validade por chunk + versão do snapshot + igualdade de `LayerVisibility`; views obsoletas não são desenhadas |
| Raio e capacidade | retenção quadrada igual à seleção; raio efetivo limitado ao maior quadrado que cabe em `max_cached_chunks` |
| UI | widgets têm precedência sobre câmera; painel mostra faixa selecionada, span visível e limites de corte |
| Config adicional | `layers.default_mode`; `interval_size` e `cumulative_direction` passaram a alimentar o renderer |

### Adotadas na Fase 8

| Decisão | Valor adotado |
|---|---|
| Escalas | LOD 0..3 = células horizontais 1/2/4/8, sempre com altura de um voxel |
| Seleção | pixels projetados por bloco a partir de distância/FOV/viewport, limitada por `lod_quality` |
| Integridade | só agrega área totalmente sólida/conhecida/visível; qualquer mistura subdivide |
| Construção | workers daemon limitados sobre `CartographicSnapshot`; nenhuma leitura de `Level` fora da game thread |
| Transição | mantém malha anterior de mesma revisão/view até o novo LOD ficar pronto |
| Cache | LRU por chunk + revisão + LOD + `LayerVisibility`, limitado por quantidade e MiB |

### Adotadas na Fase 9

| Decisão | Valor adotado |
|---|---|
| Captura | reconciliação incremental de voxels conhecidos em chunks carregados, sem depender de evento servidor |
| Segurança | posição `UNKNOWN` nunca é amostrada para escrita nem inferida |
| Justiça | 64 posições por chunk/passagem, chunks ordenados por distância e orçamento global por tick |
| Invalidação 3D | revisão do chunk + vizinhança 3×3 usada no culling |
| Invalidação HUD | revisão máxima apenas da área de chunks coberta pela imagem atual |
| Persistência | mesma dirty region e schema `CZM1`; mudança de bloco não exige migração |

### A confirmar nas próximas fases

1. **Perfil integrado:** medir frame time, memória, throughput dos jobs LOD e latência da reconciliação em mundo grande/multiplayer.
2. **Perfil de descoberta:** ajustar budgets e raios com medições reais.
3. **Migração de schema:** definir a cadeia quando surgir um formato v2; hoje versão diferente de 1 falha de forma controlada.
4. **Materiais 3D:** atlas/texturas, transparência e tint de biome continuam pendentes.

## 4. Comandos de validação (após a Fase 1)

```bash
./gradlew build
./gradlew test
./gradlew runClient
./gradlew runServer   # requer eula.txt aceito e online-mode=false
```

## 5. Fora de escopo

Ver `AGENTS.md` §20. Em especial: nenhuma dependência de outros mods de mapa, nenhum acesso a dados do servidor, nenhuma geração de chunks e nenhuma sincronização server-side.
