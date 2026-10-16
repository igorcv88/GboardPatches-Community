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

## Respostas recebidas (16/10/2026) e consequências

- `EditTextOnKeyboard(Context)` existe e não exige XML. Instanciar não instala o provedor.
- `Lnvq.ak(Loqa,Z)`: provedor não nulo é sempre aceito; o booleano chega a `Loup.aC` como `reset`. Jarvis usa `false`; `Lqsj.h` usa `true`. `ak(null, …)` só passa se `Lnvq.n` (EditorInfo do dono) não for nulo. `Lnvq.T(EditorInfo,Z)` zera `n`/`j` quando a sessão muda.
- Jarvis obtém o `Lnvq` por `JarvisPromptKeyboard.t:Lhpj` → `Lhpj.p:Lnvq`. `Jarvis.h()` não chama `ak`: a restauração do provedor não é explícita.
- Jarvis vive no slot de header (`Keyboard.eH(Lppe.a)`), com o corpo alfabético (`Lppe.b`) visível abaixo. O clipboard ocupa o corpo (`Lppe.b`: `RecyclerView 0x7f0b01bd`) e o header (`Lppe.a`).
- `pc=480/483` de `ClipboardKeyboard.e` é a tooltip de screenshot, não a lista. `Loxv.al(Lpfj)` em `m()` é troca de configuração de teclado (`showCloseButton=true`, `showExpandButton=false`), não preserva o clipboard.

**Consequência de desenho:** o Gboard só demonstra "campo interno + teclado alfabético" com o campo no **header**, sobre o corpo alfabético. Enquanto `ClipboardKeyboard` estiver ativo, o corpo é o clipboard: não há teclado alfabético para digitar. Duas opções:

1. **Busca no header sobre o alfabético (modelo Jarvis):** ao tocar na lupa, voltar ao teclado alfabético e mostrar no header o campo `EditTextOnKeyboard` + uma faixa curta de resultados (rolagem horizontal ou 2–3 linhas). Colar a partir da faixa. É o único arranjo com precedente no binário. A lista completa do clipboard não fica visível durante a digitação.
2. **Clipboard reduzido + alfabético:** exige mudar a altura de `Lppe.b`/`Lppe.a` em tempo real. Sem precedente no binário; maior risco de layout (flutuante, paisagem, Frosted Glass).

Recomendo a opção 1. A decisão é do usuário.

## Perguntas binárias restantes (para a opção 1)

1. `JarvisPromptKeyboard.t:Lhpj`: em qual classe o campo é declarado (`Keyboard`/`LifecycleKeyboard`?) e se `ClipboardKeyboard` o herda; caso não, outro caminho para o `Lnvq` a partir de `ClipboardKeyboard`/`Loxv`.
2. Restauração do provedor: quem chama `Lnvq.ak` (ou grava `Lnvq.j`) para devolver o provedor ao `Loup` depois que um teclado de header (Jarvis) é fechado. Listar os callers de `Lnvq.ak`/`aA` e de `Lnvq.T`.
3. Ativação de um teclado de header sobre o alfabético: o `Lpfj`/keyboard-def usado para abrir Jarvis (valor de `displayName`/tipo) e onde a altura máxima do slot `Lppe.a` é definida.

## Perguntas originais (já respondidas)

1. **`Lnvq;->ak(Loqa;Z)V`** (`classes.dex`, code_item `0x4c67a4`): o que o booleano controla, qual condição dispara `cannot clear focus not owned by itself` e qual chamada restaura o provedor do serviço ao sair. Incluir a decompilação de `Lnvq;->aA(Loqa;Z)V` e `Lnvl;->Z(Loqa;Z)V`.
2. **`JarvisPromptKeyboard;->K(EditTextOnKeyboard)V`** (`0x41e084`) e o método simétrico que sai do prompt: como o `Lnvq` é obtido a partir do teclado (campo/getter de `Keyboard`/`Loxv`) e quais argumentos são passados em `pc=30`.
3. **Construtores de `EditTextOnKeyboard`**: assinaturas, e se exige atributos XML/estilo ou registro adicional (listeners `Loph`, `onSelectionChanged` → `i/k/f`).
4. **Teclado alfabético com o clipboard aberto:** como `JarvisPromptKeyboard` (ou `Lqsj.h(TextView,Z)V`, `0x60bf8c`) mantém sua própria view visível enquanto o teclado principal digita. É uma view de header/extensão acima do corpo do teclado? Qual método/recurso posiciona essa view e quem define a altura.
5. **`ClipboardKeyboard`**: em `e(EditorInfo,Object)V`, o container pai em que o painel é inserido (`FrameLayout` em `pc=480,483`) e se o clipboard é um "body keyboard" que substitui o alfabético. Em `m(Lnur;)Z` `pc=187-208`, o significado de `Lpfj(String,Lvwd,Z,Z)` passado a `Loxv.al`: é a troca para outro teclado? Qual valor de `String`/`Lvwd` leva ao alfabético.

Com as respostas de 1–3 é possível implementar o roteamento da consulta. As respostas 4–5 decidem se os resultados podem ficar acima do teclado sem reescrever o layout do IME.
