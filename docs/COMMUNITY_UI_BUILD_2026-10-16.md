# Build e toolbar contextual — 16/10/2026

Base: `main` em `37dae90cf3ad0807cddddd8aec63ad4d56d8ffd1`. O PR #4 já havia sido mesclado antes desta continuação. Este complemento deve ser aberto como **ready for review**, conforme pedido do usuário; isso não autoriza merge ou release.

## As cinco falhas reportadas

1. `EditorStyledSnapshotTest` expôs uma chamada de produção incompatível com Android 26–32: [`StyleSpan.getFontWeightAdjustment()` existe desde API 33](https://developer.android.com/reference/android/text/style/StyleSpan#getFontWeightAdjustment()). O código agora inclui esse valor somente a partir de 33; em versões anteriores compara os atributos realmente disponíveis. O teste roda em 26 e 33, sem elevar artificialmente o SDK para esconder a falha.
2. Os dois testes de `StableEditorSpansTest` instanciavam `StyleSpan` e `URLSpan` em JUnit simples, usando o framework mockável do AGP. A classe agora usa Robolectric nas APIs 26 e 33. Um teste adicional na API 33 verifica que ajustes de peso diferentes continuam diferentes no descritor.
3. As duas comparações de texto de `GboardEditingAccessPointsSettingsFeatureTest` ainda esperavam a mensagem anterior ao #4, de Undo limitado à toolbar. As expectativas en/zh-Hant agora correspondem à autoridade de textos atual, que inclui captura nativa comprovada.

Não foram desabilitados testes, alteradas as expectativas de igualdade de spans nem ocultadas exceções para fazer o build passar. Os avisos de depreciação não eram a causa do erro informado.

## Seta contextual implementada no código

`GboardQuickToolbar1803Patch` conecta `Lmlh.M(View)` à instância da faixa nativa, observa a lista de candidatos na entrada (sem reter textos/modelos), a contagem real e a view renderizada após `Leso.b(List,ojt,boolean)` e sua limpeza em `Leso.v/c/close`, e instala um delegate estreito em `Lmln.m(nur)`.

`QuickToolbar1803Runtime` só decora a view `0x7f0b053e` depois de conferir seu evento primário por `SoftKeyView.h()` / `Lpnu.c == -40005`. A posição na árvore, sozinha, não autoriza a alteração. Quando os favoritos estão abertos, aceita também o evento nativo de fechamento `-40006`.

A seta usa foreground temporário e oculta somente o container visual interno da tecla. Não substitui o listener original nem a view das teclas. Sem candidatos, ao desativar a opção geral, trocar sessão, perder a view ou destacá-la da janela, restaura conteúdo, foreground e descrição. O estado acompanha a opção geral de Toolbar Editing Buttons, independentemente de habilitar/desabilitar Undo individualmente.

A abertura chama `Lmln.M(true,false,pfz.b,false)`, que popula a faixa com `Lmlh.w()` / `C(List)`: tokens favoritos ordenados e slots nativos existentes. Não chama `Lmlh.D()`, a rota do painel de todos os atalhos. O segundo toque chama a rota nativa `Lmln.y(false,false)`. O código mantém os limites/overflow e não grava preferências de ordenação. Falha ao mostrar favoritos consome o evento da seta e reverte seu estado, sem abrir o painel completo como fallback.

As callbacks internas disparadas pela própria troca são protegidas contra reentrada. O binding mantém o fundo nativo; as descrições de acessibilidade usam os textos en/zh-Hant do projeto. Não há barra inferior adicional.

## Validação executada

**46 testes locais passaram** em uma execução focada com JUnit/Robolectric 4.14.1 e classes de produção compiladas por ECJ. Inclui os testes que falharam, a matriz de spans API 26/33, as regressões de transação/sessão e três testes novos de árvore Android para a toolbar. Estes verificam troca na mesma faixa, permanência das teclas, listener original, restauração e recusa de abrir todos os atalhos quando favoritos falham.

O harness local usa IDs de recurso de fixture e catálogo gerado a partir da autoridade XML do repositório. Não é a geração/empacotamento de recursos pelo AGP nem uma execução completa dos 821 testes. O novo runtime Java também foi compilado. Catálogo ABI/hash, XML de textos e `git diff --check` foram conferidos.

Não há credencial de GitHub Packages nem Android SDK nesta sessão local. As credenciais dos secrets do Actions não ficam disponíveis aqui. O build autenticado completo, compilação Kotlin/transformação aplicada ao APK, `.mpp` final, importação Morphe e teste no aparelho permanecem pendentes; os 46 testes não substituem essas etapas. Nenhum workflow, merge ou release foi executado nesta continuação.

## Pesquisa visual: pendente

A lupa/campo/filtro ainda não estão conectados. A análise encontrou o mecanismo nativo que faltava: `com.google.android.libraries.inputmethod.widgets.EditTextOnKeyboard` implementa o provider `Loqa`, mantém `getCurrentInputConnection()` próprio e é conectado por `Lnvq.ak(Loqa,boolean)` → `aA` → `Lnvl.Z`. O próprio Gboard usa essa rota em `JarvisPromptKeyboard.K`, sem enviar a edição ao campo do aplicativo.

Isso resolve a identificação da API de foco, mas não prova o comportamento completo do clipboard. Falta montar o campo no cabeçalho nativo, fornecer a entrada de texto no contexto do painel, aplicar a apresentação filtrada sem corromper índices/listas usados por pin/exclusão/arraste, restaurar o provider original em todas as saídas e validar live updates. O simples binding de um EditText não deve ser apresentado como busca concluída.

Depois do build/importação, testar no S25 Ultra: sugestões → favoritos → sugestões sem fechar teclas; menu stock quando não há candidatos; troca de app, paisagem, senha, emojis e Frosted Glass; depois Undo/Redo no ChatGPT, WhatsApp, Chrome/EditText e cartões recentes/fixados. A janela de captura de 1.500 ms e o limite de 32.768 unidades UTF-16 do #4 não foram ampliados nem considerados validados fisicamente.
