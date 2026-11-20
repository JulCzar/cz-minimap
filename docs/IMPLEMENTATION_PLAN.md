# Plano de implementação

> **Estado:** fases 1 e 2 concluídas. A execução segue as fases do `AGENTS.md` §18, uma por vez.
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
| 3 a 10 | Não iniciadas |

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

**Tarefas:** tracking de posição/dimensão do jogador; `WorldDataSnapshot` imutável via collected chunks; descoberta de superfície com amostragem limitada; descoberta subterrânea híbrida (visibilidade + proximidade); parâmetros configuráveis; atualização incremental; preservação do já descoberto.

**Critérios de aceite:** revelação progressiva; chunks carregados não revelam terreno desconhecido; cavernas e túneis são descobertos sem revelar blocos sólidos arbitrários; troca de dimensão mantém estado separado.

**Validação:** testes manuais em superfície, caverna, túnel estreito e Nether/End; `./gradlew test`.

**Riscos:** APIs exatas de leitura de chunk em 1.21.1 devem ser confirmadas antes de codar; custo por tick precisa de orçamento e perfil.

---

### Fase 4 — Persistência

**Tarefas:** serialização de região v1; isolamento por mundo/dimensão; dirty tracking; fila assíncrona limitada e deduplicada; escrita atômica com backup; quarentena de arquivo corrompido; cadeia de migração; flush em desconexão, troca de mundo e shutdown.

**Critérios de aceite:** dados sobrevivem a reinício; mundos nunca compartilham dados; arquivo corrompido não derruba o jogo; nenhum I/O na render thread.

**Validação:** ciclos salvar/reabrir, troca de mundos, interrupção forçada de escrita, arquivo corrompido propositalmente.

**Riscos:** estabilidade da hash de identidade entre sessões; compressão escolhida; atomic move em diferentes filesystems.

---

### Fase 5 — Minimap 2D

**Tarefas:** geração de tiles a partir de snapshots; cache invalidado por versão; desenho via `GuiGraphics` no evento de HUD; posições, tamanho, forma, opacidade, zoom e orientação; indicador do jogador; configuração correspondente.

**Critérios de aceite:** render contínuo sem scan de mundo; funciona em diferentes escalas de GUI e tamanhos de janela; independente da câmera/cutaway do mapa 3D.

**Validação:** inspeção visual no cliente para cada posição, forma e orientação; perfil de frame time.

**Riscos:** evento de overlay correto em 1.21.1; custo de upload de textura a cada atualização.

---

### Fase 6 — Renderizador 3D inicial

**Tarefas:** câmera orbital independente; malhas por região com remoção de faces ocultas; `MaterialResolver` com UV do atlas e tint de biome; tratamento de transparência e fallback; invalidação em resource reload; tela com controles de navegação (zoom, reset, retorno ao jogador).

**Critérios de aceite:** modelo voxel rotacionável com texturas reconhecíveis; nenhum objeto de render por bloco; malhas reconstruídas por região, não globalmente.

**Validação:** rotação/pan/zoom, inspeção visual de terreno e cavernas, resource reload, perfil de FPS.

**Riscos:** API de renderização customizada em 1.21.1; ciclo de vida de texturas GPU; transparência.

---

### Fase 7 — Camadas verticais e cutaway

**Tarefas:** `LayerRange` e resolução ancorada no `minY` da dimensão; modos selecionado/cumulativo/completo/cutaway; recorte horizontal e vertical; controles na tela; invalidação apenas de dados de apresentação.

**Critérios de aceite:** intervalos `[min, max)` com Y real preservado; cutaway nunca revela `UNKNOWN`; múltiplos intervalos; limites por dimensão.

**Validação:** testes visuais por intervalo, cumulativo e cutaway, incluindo dimensões com alturas diferentes.

**Riscos:** manter a separação entre filtro de apresentação e modelo.

---

### Fase 8 — LOD híbrido

**Tarefas:** níveis 0..3; seleção por distância/tamanho projetado; agregação com simplificação de material; construção assíncrona a partir de snapshots; cache LRU com orçamento; continuidade entre regiões; compatibilidade com camadas/cutaway.

**Critérios de aceite:** grandes áreas inspecionáveis sem detalhe integral; transições sem buracos; cavernas e fronteiras de desconhecido preservadas; rebuild apenas da região afetada.

**Validação:** testes por nível, distância, fronteira de região e cutaway; medição de memória e picos de frame.

**Riscos:** agregação atravessando ar conhecido/desconhecido; throttle de jobs.

---

### Fase 9 — Sincronização de mudanças

**Tarefas:** captura de mudanças de bloco recebidas pelo cliente; atualização do modelo descoberto; invalidação de tiles, malhas e LOD; verificação em multiplayer.

**Critérios de aceite:** mudanças recebidas aparecem nas regiões descobertas; nenhum rebuild global; nada fora da área conhecida é inferido.

**Validação:** editar blocos em singleplayer e multiplayer; observar HUD e mapa 3D.

**Riscos:** eventos de update disponíveis no cliente 1.21.1; deduplicação de invalidações.

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
| Armazenamento (Fase 4) | diretório `.minecraft/czminimap` com compressão LZ4; o formato versionado será definido para esse destino |

### A confirmar nas próximas fases

1. **Política de descoberta** (Fase 3): limites de amostragem por tick e limiar de visibilidade para superfície/subterrâneo.
2. **Formato de região v1** (Fase 4): campos exatos do cabeçalho, tamanho de chunk de região no arquivo e esquema de migração.
3. **Estratégia de cache** (Fases 5/6): limites de memória e chaves de invalidação por `(region, version, modo)`.

## 4. Comandos de validação (após a Fase 1)

```bash
./gradlew build
./gradlew test
./gradlew runClient
./gradlew runServer   # requer eula.txt aceito e online-mode=false
```

## 5. Fora de escopo

Ver `AGENTS.md` §20. Em especial: nenhuma dependência de outros mods de mapa, nenhum acesso a dados do servidor, nenhuma geração de chunks e nenhuma sincronização server-side.
