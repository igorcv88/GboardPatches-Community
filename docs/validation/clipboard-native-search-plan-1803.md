# Busca do clipboard com o teclado nativo — plano (18.0.3)

Fonte binária: relatório dirigido de 16/10/2026 sobre o APK SHA-256 `3099262121…05e5b3` (análise estática feita fora desta sessão). Nada abaixo está implementado.

## Mecanismo a reutilizar

O Gboard já digita em campos internos com o teclado alfabético completo:

1. `EditTextOnKeyboard` (implementa `Loqa`) cria seu próprio `InputConnection` a partir de `EditText.onCreateInputConnection` (`e(EditText,EditorInfo)`), embrulhado em `Lstj` (`j()` → `Lstj.setTarget`).
2. `JarvisPromptKeyboard.K(EditTextOnKeyboard)` conclui a composição e chama `Lnvq.ak(Loqa;Z)V`, que troca o provedor de conexão (`updateInputConnectionProvider`) via `Lnvq.aA` → `Lnvl.Z` → `Loup.Z` → `Loup.aC(Loqa;ZZ)V` → `Loqe.n(Loqa)`.
3. A partir daí `Loqe.d()` consulta `Loqa.getCurrentInputConnection()` do campo interno; o app hospedeiro não recebe a digitação. `EditTextOnKeyboard.dH()` fecha a conexão local.

Isso substitui o teclado ASCII de 34 dp de `ClipboardSearchUiAdapter` e o `setShowSoftInputOnFocus(false)`.

## Desenho proposto

1. **Campo:** criar um `EditTextOnKeyboard` (não um `EditText` comum) no header do clipboard (`0x7f0b01c9`), no lugar do `EditText` atual.
2. **Foco:** ao tocar na lupa, chamar `Lnvq.ak(campo, <bool>)` como em `JarvisPromptKeyboard.K`. Ao sair (Back, X, colar, fechar painel, `ClipboardKeyboard.h()`), restaurar o provedor do serviço com `Lnvq.ak(Loup, <bool>)` e fechar o campo (`dH()`).
3. **Layout:** manter o `ClipboardKeyboard` vivo (não chamar `h()`) e exibir o teclado alfabético abaixo dele. Essa é a parte sem evidência: ver perguntas 3–5.
4. **Isolamento:** a consulta continua indo só para `ClipboardSearchController`; o journal de Undo não vê esse campo (o provedor deixa de ser `Loup`). Teste-canário no Termux: o termo pesquisado não pode aparecer no shell.

## Perguntas binárias necessárias (pedir à outra sessão)

1. **`Lnvq;->ak(Loqa;Z)V`** (`classes.dex`, code_item `0x4c67a4`): o que o booleano controla, qual condição dispara `cannot clear focus not owned by itself` e qual chamada restaura o provedor do serviço ao sair. Incluir a decompilação de `Lnvq;->aA(Loqa;Z)V` e `Lnvl;->Z(Loqa;Z)V`.
2. **`JarvisPromptKeyboard;->K(EditTextOnKeyboard)V`** (`0x41e084`) e o método simétrico que sai do prompt: como o `Lnvq` é obtido a partir do teclado (campo/getter de `Keyboard`/`Loxv`) e quais argumentos são passados em `pc=30`.
3. **Construtores de `EditTextOnKeyboard`**: assinaturas, e se exige atributos XML/estilo ou registro adicional (listeners `Loph`, `onSelectionChanged` → `i/k/f`).
4. **Teclado alfabético com o clipboard aberto:** como `JarvisPromptKeyboard` (ou `Lqsj.h(TextView,Z)V`, `0x60bf8c`) mantém sua própria view visível enquanto o teclado principal digita. É uma view de header/extensão acima do corpo do teclado? Qual método/recurso posiciona essa view e quem define a altura.
5. **`ClipboardKeyboard`**: em `e(EditorInfo,Object)V`, o container pai em que o painel é inserido (`FrameLayout` em `pc=480,483`) e se o clipboard é um "body keyboard" que substitui o alfabético. Em `m(Lnur;)Z` `pc=187-208`, o significado de `Lpfj(String,Lvwd,Z,Z)` passado a `Loxv.al`: é a troca para outro teclado? Qual valor de `String`/`Lvwd` leva ao alfabético.

Com as respostas de 1–3 é possível implementar o roteamento da consulta. As respostas 4–5 decidem se os resultados podem ficar acima do teclado sem reescrever o layout do IME.
