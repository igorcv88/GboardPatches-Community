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

## Decisão (16/10/2026): opção 2 — resultados acima, teclado alfabético nativo abaixo

### Arquitetura implementada (PR separado, ainda desligada)

| Peça | Mecanismo | Estado |
| --- | --- | --- |
| Superfície de resultados | Área de candidatos do próprio `InputMethodService` (`setCandidatesView` / `setCandidatesViewShown`), acima da input view do Gboard. API pública do framework; não redimensiona `Lppe.b` nem altera o layout do Gboard | Implementada |
| Teclado | O teclado alfabético nativo do Gboard, no corpo normal | Troca clipboard → alfabético **sem binding** (A1) |
| Campo | `EditTextOnKeyboard(Context)` (implementa `Loqa`) dentro da superfície | Implementado |
| Roteamento | `Loup.Z(Loqa;Z)V` com `reset=false` (mesmo valor de `JarvisPromptKeyboard.K`) → `Loup.aC` | Ida implementada; volta **sem binding** (A3) |
| Dados | Snapshot imutável dos clips de texto (`Lfjk.n`, ordem nativa, texto completo; sensíveis, cabeçalhos e não-texto excluídos) | Implementado |
| Busca | `ClipboardSearchController`/`Matcher` existentes (texto completo, acento/caixa, worker cancelável) | Implementado |
| Colar | Restaura o provedor e faz `commitText` na conexão do serviço (passa pelo journal de Undo) | Implementado |
| Fixar/apagar | Ações nativas do card | **Sem binding** (A6); fora da superfície por enquanto |
| Fim de sessão | Hook em `Loup.onFinishInputView/onFinishInput/onWindowHidden/onDestroy` restaura provedor e superfície | Implementado |
| Back | Hoje fecha a IME (o hook de fim restaura tudo); cancelar a busca com Back exige A7 | Pendente |

`ClipboardNativeSearchSession` garante a ordem: abre superfície → alfabético → roteia (por último); fecha restaurando o provedor **primeiro**, depois esconde a superfície; cancelar volta ao clipboard; colar fica no alfabético. Falha em qualquer passo desfaz os anteriores. `ClipboardKeyboard.h()` não encerra a busca (a troca para o alfabético o dispara de propósito).

**Falha fechada:** `ClipboardNativeSearch1803.available()` só retorna `true` quando as quatro flags `*_REVIEWED` forem verdadeiras. Até lá o botão de busca não aparece; o teclado de 34 dp e o filtro dentro do painel foram removidos. Ou seja, este PR, como está, **remove** a busca atual e só a devolve na forma nova quando os bindings forem confirmados.

Superfície independente com as ações nativas (mover o `RecyclerView` 0x7f0b01bd e o adapter `Lfjk` para a superfície): avaliada e não adotada agora. Exige impedir a limpeza de `ClipboardKeyboard.h()` (observadores, RecyclerView, `Lfkh.a`) e provar que `ClipboardKeyboard.D(Lfih;Z)` funciona com o teclado inativo (A8). Fica como alternativa se A6 não tiver API de dados utilizável.

### Perguntas binárias (classe / método / o que esclarecer)

- **A1 — clipboard → alfabético.** Em `ClipboardKeyboard`, o handler do botão de voltar/“ABC” do header: método, código de evento (`Lnur`) e a chamada que troca de teclado (ex.: `Loxv.al(Lpfj)` e o valor de `displayName`/`Lvwd`). A troca chama `ClipboardKeyboard.h()` de forma síncrona?
- **A2 — alfabético → clipboard.** A chamada que abre o `ClipboardKeyboard` a partir do teclado alfabético (clique do access point de clipboard): método, código de evento ou `Lpfj` usado.
- **A3 — restauração do provedor.** Corpo de `Loup;->aC(Loqa;ZZ)V` (`0x2eedf8`) e `Loup;->C(Loqe;ZZ)V`: o que acontece quando o provedor é o próprio `Loup` (é assim que se restaura?), quem instala o `Loup` como provedor no início da sessão (callers de `aC` com `p0`), e se `aC`/`C` chamam `onStartInput`, `onStartInputView`, `onFinishInputView` ou `onFinishInput` do próprio `Loup` (isso encerraria a busca pelo hook de fim).
- **A4 — área de candidatos.** `Loup;` (ou superclasses até `InputMethodService`) declara `onComputeInsets(InputMethodService$Insets)`, `setCandidatesViewShown(Z)`, `onCreateCandidatesView()`, `onEvaluateInputViewShown()` ou `onConfigureWindow(...)`? Se declara `onComputeInsets`: valores de `touchableInsets`/`touchableRegion`/`contentTopInsets`. Existe algum caller de `setCandidatesView`/`setCandidatesViewShown` no APK?
- **A5 — `EditTextOnKeyboard` fora do Jarvis.** `getCurrentInputConnection()` (`0x67a024`) / `j()` (`0x67ab14`): qual `EditorInfo` é usado em `e(EditText,EditorInfo)` e se o campo precisa estar anexado/focado antes de virar provedor. Algum listener (`Loph`) precisa ser registrado?
- **A6 — fixar/apagar por id.** Métodos de `Lfjk`/`ClipboardKeyboard`/`Lfjv` usados pelas ações do card (fixar, desafixar, apagar) para um `Lfih` ou id: assinatura e se exigem o teclado ativo.
- **A7 — Back.** `Loup;` declara `onKeyDown(ILandroid/view/KeyEvent;)Z`/`onKeyUp`? Como `KEYCODE_BACK` é tratado (para cancelar a busca em vez de fechar a IME).
- **A8 — (alternativa) colar nativo fora do painel.** `ClipboardKeyboard.D(Lfih;Z)V` (`0x37713c`) depende de campos zerados por `h()` (`0x378264`)?

## Extração A1–A8 (16/10/2026) aplicada ao PR #17

| Item | Aplicado no código | Flag |
| --- | --- | --- |
| A3 restauração | `restoreRouting()` = `Loup.Z(null,false)` ≡ `aC(null,false,true)`, a mesma chamada que `Loup.onFinishInputView` faz (0x2ee044, pc≈128–130). Não usa `Z(Loup,false)`. Rollback restaura também quando o roteamento falha ou lança exceção (`Loqe.n` roda em pc≈45, antes do ramo de `Loup.C`) | `ROUTING_RESTORE_REVIEWED=false` |
| A3 lifecycle | `end()` vindo de `onFinishInputView/onFinishInput/onWindowHidden/onDestroy` durante a abertura é adiado e aplicado logo depois (a troca de teclado não desmonta uma sessão pela metade) | — |
| A4 insets | Captura do `Insets` na entrada de `Loup.onComputeInsets` (`p1`, garantido) e ajuste na saída sem registradores. Só com a busca aberta e a superfície visível: `REGION` → `touchableRegion ∪ superfície`; sempre `visibleTopInsets = min(nativo, topo da superfície)`; `contentTopInsets` intocado. Fora da busca, nada muda (Frosted Glass e insets nativos preservados) | `INSETS_REVIEWED=false` |
| A4 visibilidade | `showSurface()` só confirma o anexo; 500 ms depois, se a superfície não estiver visível com altura > 0, a busca é encerrada | — |
| A5 campo | Igual a `JarvisPromptKeyboard.K`: campo anexado e focado, `getCurrentInputConnection()` não nulo, depois `Z(campo,false)` | — |
| A7 Back | Hook na entrada de `Loup.onKeyDown/onKeyUp`: com a busca aberta, Back é consumido e o UP cancela a busca (volta ao clipboard). Sem busca, o caminho nativo roda intacto | — |
| A8 | Mantido o snapshot + `commitText`; `D()` não é usado | — |
| Colar | Recusa se a conexão atual for a do `InputBinding` (dummy, mesma regra de `Loup.az()`) | — |
| A6 fixar/desafixar | `ClipboardKeyboard.Q(Lfih;I)V` exige o `ClipboardKeyboard` vivo, que deixa de existir quando o alfabético entra. Não implementado na superfície | — |

### Ainda bloqueado (pedidos exatos)

- **T1a (troca clipboard → alfabético):** o id de recurso do botão do header do clipboard que volta ao teclado (`res/crz.xml`, `res/_f7.xml`, `res/bAF.xml`) e o código de evento `Lnur` que ele emite. Com o id, a troca pode usar o próprio handler nativo (`performClick`) em vez de montar um `Lpfj`.
- **T1b (alfabético → clipboard):** o código de evento (`Lnur`/keycode) emitido pelo Access Point de clipboard e o método que o despacha (ex.: `Loxv.M(Lnur)`), com a assinatura da fábrica de `Lnur` usada nesse caminho.
- **T2 (`Loup.aC`):** o predicado que leva ao `Loup.C(Loqe;ZZ)V` em pc≈60–72 (corpo `throw null`). Se o roteamento para `EditTextOnKeyboard` puder satisfazê-lo, a troca lança `NullPointerException`. E o que `Loqe.n(null)` faz com o provedor anterior.
- **T3 (aparelho):** com a busca aberta, `adb shell dumpsys input_method` (insets e região tocável) e o toque na superfície em retrato, paisagem e flutuante.
- **T4 (apagar):** método persistente de exclusão de um `Lfih` em `Lfke`/`Lfjz`, que funcione sem o `ClipboardKeyboard` aberto. O mesmo vale para fixar/desafixar pelo controlador de dados, e não pelo `ClipboardKeyboard.Q`.

## Extração T1/T2/T4 (16–17/10/2026) aplicada

### T2 — roteamento e restauração com o despacho virtual correto

`LatinIME extends Leqy extends Loup`. `Loup.C(Loqe;ZZ)V` (`throw null`) é stub; o `invoke-virtual` em `Loup.aC` pc=72 resolve para `Leqy.C` (0x42ba84), que grava `Loup.v` = o `Loqe` recebido e reaplica o contexto (`Loqe.c()` EditorInfo, `Looa`, `Lnvu/Lowq`, `Loto`).

Fluxo de `aC` (0x2eedf8) aplicado aos dois passos da busca:

| Chamada | Efeito em `aC` | `Leqy.C` |
| --- | --- | --- |
| `Z(campo,false)` | `Loup.t.n(campo)`; a conexão muda → pc=58 → `C(t, true, campo.dR())` | `Loup.v = t` (wrapper do campo) |
| `Z(null,false)` (restauração) | `Loup.t.n(null)` (fecha o campo via `dH()`); `v3=0`; `Loup.v` (= `t`) ≠ `Loup.r` → pc=72 → `C(r, true, false)` | `Loup.v = r` (wrapper do serviço/editor) |

Ou seja, a restauração não “recupera” o provedor antigo: ela desativa o wrapper de override `t` e reativa o wrapper base `r`, que é o do editor do app. É o mesmo caminho da limpeza de `onFinishInputView`. Evidência estática forte. `ROUTING_RESTORE_REVIEWED` continua `false` até o aparelho confirmar `getCurrentInputConnection()`/`az()` após a volta.

### T1a — voltar ao alfabético

`0x7f0b0521` = `key_pos_clipboard_back_to_prime` (`_f7.xml`, `bAF.xml`). Implementado em `ClipboardNative1803`: lê a ação primária do próprio `SoftKeyView` (`h()` → `Lpnu`), cria o evento com `Lnur.d(Lpnu)` e despacha por `ClipboardKeyboard.x` (`Loxv.M(Lnur)`). Não usa `performClick()` nem inventa código. A cada processo, ao abrir o clipboard, o log registra uma vez `Clipboard back-to-prime key: <classe> code=<n>` (sem conteúdo), para o trace no aparelho. Continua com `ALPHABET_SWITCH_REVIEWED=false`.

### T1b — voltar ao clipboard

Sem binding. `returnToClipboard()` continua vazio.

### T4 — apagar

`ClipboardNative1803.delete(ctx, ids)` chama `Lfjo.h(Context,List<Long>)` (o mesmo método que o prune customizado do repositório já usa). Como `h` engole falhas, o sucesso só é aceito se a URI de cada item (`Lfjo.d(ctx,1,id)`) não retornar mais linhas. Roda fora da thread principal, e a linha só sai da lista depois da confirmação. Na superfície: toque longo marca a linha (“Tap again to delete”), um toque na mesma linha confirma e qualquer outro toque cancela. Flag própria `DELETE_REVIEWED=false`, independente da busca. Fixar e desafixar continuam fora.

### Pedidos restantes

- **T1a′:** `SoftKeyView;->h()` — tipo de retorno exato (é `Lpnu`?) e o método que, num toque, transforma essa ação em evento e o despacha. Ele usa `Lnur.d(Lpnu)` e `Loxv.M(Lnur)`, ou outro dispatcher (`Lnvq.M`)? Pode ser respondido pelo trace no aparelho com o log acima.
- **T1b (trace no aparelho):** o código numérico do evento do Access Point de clipboard e o dispatcher, com a ordem de `ClipboardKeyboard.e()`/`h()`.
- **T3 (aparelho):** área tocável e insets com a superfície aberta.
