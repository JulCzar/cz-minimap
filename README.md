# CZ Minimap

Minimap standalone e client-side para **Minecraft 1.21.1 / NeoForge**, com duas visualizações alimentadas pela mesma fonte de dados cartográficos:

- **Minimap 2D** sobreposto ao HUD;
- **Mapa 3D interativo** em tela cheia, com câmera orbital, camadas verticais e cutaway;
- descoberta progressiva de superfície e subterrâneo;
- persistência em armazenamento local por mundo/dimensão.

As regras normativas do projeto (escopo, arquitetura, fases e critérios de aceite) estão em [`AGENTS.md`](AGENTS.md).

## Estado atual

**Fases 1, 2 e 3 implementadas.** O projeto compila, gera `build/libs/czminimap-0.1.0.jar` e carrega no Minecraft 1.21.1 / NeoForge 21.1.267. A Fase 2 introduziu o modelo cartográfico (identidades, voxels, estados de descoberta, snapshots versionados). A Fase 3 acrescentou a descoberta progressiva: leitura apenas de chunks já carregados no cliente, varredura de superfície por heightmap, expansão limitada por ar conectado no subterrâneo e orçamento por tick. A suíte tem 16 testes JUnit 5. As fases 4 a 10 ainda não foram iniciadas; em particular, o mapa ainda não é persistido entre sessões.

Para compilar e testar, use um **JDK 21 completo** (com `javac`) via `./gradlew build` e `./gradlew test`. Neste ambiente o Java 21 do sistema é apenas JRE, então `JAVA_HOME` deve apontar para um JDK 21 instalado.

## Documentação

- [Arquitetura proposta](docs/ARCHITECTURE.md) — fronteiras entre subsistemas, modelo de dados, fluxos, threading e formato de armazenamento.
- [Plano de implementação](docs/IMPLEMENTATION_PLAN.md) — versões verificadas, tarefas por fase, critérios de aceite, comandos de validação e decisões em aberto.

## Stack planejada

| Item | Valor |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x (`21.1.267` verificado no Maven) |
| Java | 21 |
| Gradle | 9.2.1 via wrapper |
| Plugin de build | ModDevGradle 2.0.148 |
| `mod_id` | `czminimap` |
| Pacote base | `com.czminimap` |
| Side | cliente (`DIST.CLIENT`) |

## Fora de escopo

Não são objetivos deste projeto: depender de JourneyMap/Xaero's, acessar dados do servidor, forçar carregamento de chunks, gerar terreno inexplorado ou fornecer sincronização server-side. Ver `AGENTS.md` §20.
