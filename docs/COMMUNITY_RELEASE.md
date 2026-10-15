# Releases e fonte Morphe do fork

O workflow `.github/workflows/release.yml` concentra testes, contratos, build, cache, versão e publicação. É exclusivamente manual: não roda em PRs, pushes de branches ou pushes de tags. Não usa `upload-artifact` nem publica APK. O fluxo de previews continua no mesmo workflow, pelo modo manual `theme-previews`, informando uma tag `custom-theme-previews-v*` que corresponda ao commit selecionado.

Execute manualmente **Release** a partir da branch padrão depois de integrar o código. Deixe `tag` vazio para incrementar o patch da maior versão SemVer existente, considerando 3.12.0 como base local. Assim, a próxima versão será 3.12.1 se não houver tag mais recente. Um valor explícito é aceito somente se for maior que a versão estável atual. A opção de prerelease permite uma linha de versão separada, sem alterar o ponteiro estável ou Latest. As execuções são serializadas. O workflow confere se a tag corresponde ao commit compilado antes e depois do build, e recusa sobrescrever releases existentes.

Os assets são `patches-VERSAO.mpp`, `patches-bundle.json` e `SHA256SUMS.txt`, na seção **Releases**. A release é preparada como rascunho e fica visível após o upload. Em uma release estável, o workflow também atualiza somente `patches-bundle.json` na branch padrão, usando o SHA atual desse arquivo como condição de escrita. O manifest inicial fictício foi removido: a primeira publicação estável cria o arquivo. A versão compilada vem de `-Pversion`, sem precisar alterar `gradle.properties` a cada release. Uma prerelease não altera o ponteiro estável.

A configuração de cache guarda distribuição/dependências/Gradle build cache e runtimes do Robolectric. Os testes e a compilação usam uma única invocação Gradle. O workflow também importa o `.mpp` final com o loader JVM real do Morphe antes de publicar, verificando o catálogo de patches e a origem das classes. A inspeção estrutural verifica os dois DEX, seus tamanhos/checksums/tabelas, catálogo e atributos de origem/versão. Isso não substitui importação Android no aplicativo. O ganho depende de cache disponível e das tarefas que aceitam reutilização; ainda não há medição de build deste fork.

O secret necessário continua sendo `MORPHE_PACKAGES_TOKEN`, com acesso de leitura ao registry `MorpheApp/registry`. `github.token` publica a release e atualiza os metadados. Proteções da branch devem permitir a atualização desse arquivo pelo workflow; caso contrário, a release pode ser publicada, mas a atualização do ponteiro será recusada e o job informará a falha.

Não é necessário decodificar `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` ou `KEY_PASSWORD` neste build: esses secrets assinam APKs, e o resultado é um bundle de patches. O campo `signature_download_url` fica vazio. A assinatura do APK final é feita pelo Morphe, usando a chave guardada no aplicativo. Para instalar atualizações sobre o teclado existente, preserve essa chave, inclusive ao reinstalar ou limpar os dados do Morphe.

Após a primeira release deste fork, adicione em **Sources → + → Remote**:

`https://github.com/igorcv88/GboardPatches-Community`

Também é possível usar o JSON atualizado da branch padrão ou o asset estável:

`https://github.com/igorcv88/GboardPatches-Community/releases/latest/download/patches-bundle.json`

A atualização da fonte baixa os patches novos. Para atualizar o teclado, é necessário aplicar os patches novamente ao APK pelo Morphe. Este trabalho não publicou uma release nem testou o download dessa fonte dentro do aplicativo.

Referências consultadas: [workflow do DFReroot-S25U](https://github.com/igorcv88/DFReroot-S25U/blob/main/.github/workflows/release.yml), [fontes do Morphe](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md), [atualização de aplicativos](https://github.com/MorpheApp/morphe-manager/blob/main/docs/updating-patched-apps.md) e [chave de assinatura](https://github.com/MorpheApp/morphe-manager/blob/main/docs/backup-and-keystore.md).


## Reparo sem novo build

Se a release ficar pública mas o PUT de `patches-bundle.json` falhar, execute o mesmo workflow com `operation = repair-metadata` e a tag exata, como `v3.12.1`. Esse modo não usa Gradle nem o token do registry: baixa os assets da release existente, confere publicação estável, tag/commit, nomes, checksums, versão/origem/formato do manifest e estrutura do bundle. Depois atualiza somente o ponteiro com condição pelo SHA do arquivo. Executar novamente sobre metadados idênticos não grava outro commit.

Versões antigas não podem substituir um ponteiro ou Latest mais recente. Metadados diferentes para a mesma versão são recusados. Um conflito de SHA ou bloqueio da branch continua sendo informado; não há sobrescrita forçada nem retry que ignore mudança concorrente.

## Validação desta revisão

Passaram 17 testes Python, dois testes JVM que geram JSON pelo script real e usam o desserializador `kotlinx.datetime.LocalDateTime` do Morphe, `actionlint` e a conferência de diff. `created_at` usa os campos UTC sem sufixo de timezone; o formato com offset foi confirmado como rejeitado pelo desserializador real. O bloco canônico `patches.about` agora usa o nome, fonte, contato e website do fork, mantendo o autor original nos créditos.

O smoke do `.mpp` completo foi acrescentado como gate, mas não executado: este ambiente ainda não resolve o plugin privado Morphe para gerar o artefato. Nenhuma release ou execução de Actions foi iniciada. O workflow e a importação Android permanecem não validados de ponta a ponta.
