# CZ Minimap

> **Aviso sobre desenvolvimento com IA:** este projeto está sendo desenvolvido de forma intensiva com auxílio de ferramentas de inteligência artificial. Isso inclui pesquisa de APIs, desenho de arquitetura, implementação de código, testes, depuração e documentação. A intenção é deixar isso explícito, não ocultar o uso de IA. A responsabilidade final pelas decisões, revisões e pelo resultado continua sendo do mantenedor do projeto.

Minimap standalone e client-side para **Minecraft 1.21.1 / NeoForge**, com duas visualizações alimentadas pela mesma fonte de dados cartográficos:

- **Minimap 2D** sobreposto ao HUD;
- **Mapa 3D interativo** em tela cheia, com câmera orbital, camadas verticais e cutaway;
- descoberta progressiva de superfície e subterrâneo;
- persistência em armazenamento local por mundo/dimensão.

As regras normativas do projeto (escopo, arquitetura, fases e critérios de aceite) estão em [`AGENTS.md`](AGENTS.md).

## Estado atual

**Fases 1 a 5 implementadas.** O projeto compila, gera `build/libs/czminimap-0.1.0.jar` e carrega no Minecraft 1.21.1 / NeoForge 21.1.267. A Fase 2 introduziu o modelo cartográfico (identidades, voxels, estados de descoberta, snapshots versionados). A Fase 3 acrescentou a descoberta progressiva: leitura apenas de chunks já carregados no cliente, varredura de superfície por heightmap, expansão limitada por ar conectado no subterrâneo e orçamento por tick. A Fase 4 adicionou persistência regional versionada (`CZM1`, LZ4 + CRC32) em `<gameDir>/czminimap`, com carga assíncrona protegida por gate, escrita atômica com `.bak`, quarentena `.corrupt` e flush em troca de mundo, desconexão e shutdown. A Fase 5 adicionou o minimap 2D no HUD: o modelo mantém um índice derivado da superfície descoberta, o HUD gera um buffer ARGB a partir de snapshots versionados e faz upload de uma `DynamicTexture` somente na render thread. Há posicionamento configurável (incluindo `CUSTOM`), largura/altura, máscara circular ou quadrada, opacidade do mapa e do painel, zoom (config + atalhos), orientação norte-acima/cabeçalho-acima, indicador do jogador, bússola e coordenadas. O HUD não acessa `Level`, não faz scan de mundo e não depende da câmera/cutaway do mapa 3D. A suíte tem 39 testes JUnit 5. As fases 6 a 10 ainda não foram iniciadas; não há visualização 3D.

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
