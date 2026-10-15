# Resposta técnica à revisão dos PRs #1, #2 e #3

Heads conferidos antes das alterações: #1 `65aca06eab40314f042f111b17548eee1e6ec98e`, #2 `984f56e901ad201ece4186150944effeb75569e5`, #3 `a54a8f05b2b5b5106dd0c7ed8e32cfa02223cd97`. Os quatro comentários inline também foram lidos. Os PRs estão com `draft=false`; não foi alterado esse estado nem houve merge/publicação.

| Item | Tratamento nesta revisão |
| --- | --- |
| P0-A | Escopo corrigido na configuração/documentação e testado como toolbar/long-press Paste/Cut. Cartão nativo e host continuam sem fallback local; falta mapear a rota no APK 18.0.3. |
| P0-B | Continua infraestrutura, sem seta/lupa/views. Não foi inventada assinatura da 18.0.3 a partir da beta. |
| P0-C | Hipótese de ausência de compilação/IDs não confirmada no Morphe 1.8.0: FULL mode executa ResourceIdProcessor e encoder. Teste real do encoder/decode conferiu tabela e XML binário dos dois vetores; tint e diagnóstico foram adicionados. Registro visual no Gboard ainda pendente. |
| P1-D | Comparação por descritores estáveis de spans suportados; spans desconhecidos recusam journal. Testes de instâncias distintas passaram; teste Robolectric de SpannedString adicionado, ainda não executado. |
| P1-E | Limite e exclusões preservados e descritos na configuração. Teste de campo maior que 32k e inserção externa sem journal. |
| P1-F | Callbacks intermediários próprios só são tolerados com leitura fresca comprovando o estado final no mesmo editor. Testes de callback tardio e alteração externa passaram; sequência real ainda pendente. |
| P1-G | Todos os modos são workflow_dispatch; nenhum push de tag dispara build ou preview. |
| P1-H | Data sem offset; JSON gerado de verdade foi aceito pelo desserializador de LocalDateTime usado pelo Morphe. O formato anterior foi rejeitado pelo teste. |
| P1-I | patches.about atualizado; gate confere atributos no bundle final. |
| P1-J | Checagens SemVer antes do build/publicação e antes do PUT; ponteiro/Latest não podem regredir. Testes fora de ordem e versão igual. |
| P1-K | Modo manual repair-metadata valida release/assets/tag/manifest/checksums e repara somente o ponteiro, de forma idempotente e sem build. |
| P2-L | Chave de busca sem lowercase contextual; sigma grego e expansões Unicode em vários offsets de chunk. |
| P2-M | Factory de main thread e benchmark local adicionados. Device p95, memória e captura de projeções no adaptador real continuam pendentes. |
| P2-N | Conjunto combinado de testes locais executado; build autenticado, APK e UI multiapp não executados. |
| P2-O | Contagem/ordem/overflow existentes preservados; estado visual enabled/disabled e aparência física permanecem pendentes. |
| P2-P | Inspeção estrutural mais estrita e smoke de importação do `.mpp` final pelo Morphe JVM loader acrescentado ao release. Smoke completo não executado sem build autenticado. |
| P2-Q | Cabeçalho/badges/fonte do fork corrigidos; botão de fonte pronta e JSON que apontava a release inexistente removidos. Primeiro release estável cria o manifest. |

Validação combinada: **41 testes JVM + 17 testes Python = 58**, além de actionlint e diff. Os JVM incluem 38 de edição/lógica de busca, um round trip de recursos com o encoder real e dois do contrato de data com o serializer real. O teste Robolectric novo e o smoke do bundle completo não estão nessa contagem. Nenhum conteúdo de clipboard, backup privado ou credencial entrou nos commits/logs.

O benchmark isolado de cinco buscas completas em 500 × 200.000 unidades UTF-16 deu p50 398,0 ms e p95 de cinco amostras 525,7 ms na JVM do host. Não é medição no S25 Ultra nem da UI. Os detalhes de escopo e evidências estão nos três documentos COMMUNITY_* de cada PR.

Permanecem bloqueios para afirmar entrega completa: binário correto 18.0.3 para as views/rota de colagem nativa; build autenticado/importação real do bundle; registro/aparência/foco e operações de edição/clipboard em aparelho. A revisão não autoriza publicação automática e nenhuma foi feita.
