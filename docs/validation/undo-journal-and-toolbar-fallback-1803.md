# Undo/Redo independente do app e fallback da toolbar — 18.0.3

Base: 3.12.2 (`564c17f`). O APK 18.0.3 (SHA-256 `30992621…05e5b3`) **não** estava disponível nesta sessão; nenhum símbolo ofuscado novo foi introduzido. O Frosted Glass não foi alterado.

## Undo/Redo (PR A)

Causa: `EditorActionSession` só registrava Paste/Cut. Digitação comum era delegada a `performContextMenuAction(android.R.id.undo)`, que muitos editores ignoram.

Mudança:

- `GboardEditingAccessPointsLifecyclePatch` adiciona a `Loup;` (dono do `InputMethodService`, já revisado) um override de `getCurrentInputConnection()` que chama o super e passa o resultado por `GboardEditingAccessPointsRuntime.wrapInputConnection`. Todas as chamadas do Gboard, inclusive as tipadas contra `InputMethodService`, despacham virtualmente para esse override. Se `Loup;` já declarar o método, o patch não o reescreve e o runtime registra `Undo journal inactive` no log.
- `JournalingInputConnection` (subclasse de `InputConnectionWrapper`) repassa todas as chamadas sem alteração. Chamadas que alteram texto (`commitText`, `setComposingText`, `deleteSurroundingText[InCodePoints]`, `commitCompletion`, `commitCorrection`, `sendKeyEvent` ACTION_DOWN) são envolvidas por snapshots completos (`getExtractedText`, até 32.768 unidades UTF-16). Lotes `beginBatchEdit`/`endBatchEdit` geram um único par de snapshots. A identidade é estável por conexão (WeakHashMap).
- `ImeEditJournal` (Java puro) deriva cada passo por diff prefixo/sufixo sem dividir pares surrogate. Agrupa a palavra digitada, composição e autocorreção dentro dela; espaço/quebra de linha fecha a palavra; backspaces consecutivos formam um passo; pausa > 2 s ou movimento de cursor abre novo passo. Limite de 100 passos.
- Qualquer snapshot divergente do último estado registrado (alteração do app, cursor movido, colagem pela toolbar) descarta todo o histórico. Undo/Redo só agem se o editor mostra exatamente o estado registrado; o resultado é relido e, se diferir do esperado, o histórico é descartado.
- A reversão usa a conexão crua (não é registrada como edição do teclado): `finishComposingText`, `setSelection` na faixa conhecida, `commitText`, restauração da seleção, em um batch.
- Isolamento: histórico por conexão, zerado em `onStartInput`/`onStartInputView`/`onFinishInput*`/`onWindowHidden`/`onDestroy`. Senhas, `IME_FLAG_NO_PERSONALIZED_LEARNING`, campos não textuais e Undo desligado: nenhum snapshot é feito.
- Prioridade em `EditorActionSession`: journal do teclado → transação de colagem existente → Undo do app hospedeiro.

Limites conhecidos:

- Custo: dois `getExtractedText` por lote de edição enquanto o recurso está ativo. Em campos > 32.768 caracteres ou que não fornecem texto extraído completo, o journal fica inativo e vale o comportamento anterior.
- Termux (TerminalView) não fornece texto extraído: não há como reconstruir histórico; continua delegado ao app. Esperado, não corrigível pelo teclado.
- Edições feitas pelo app (incluindo Paste/Cut da toolbar via menu de contexto) não entram no journal; são tratadas pela transação de colagem existente ou pelo app.

## Toolbar contextual (PR B)

Causa: `handleEvent` só interceptava `-40005` com o binding visual ativo (`binding != null` e manager igual ao do controller). Quando `onControllerView`/`onCandidates` falhavam em algum pré-requisito, o retorno era silencioso e o evento seguia para `Lmlh.D` (painel completo sobre as teclas).

Mudança:

- Com o binding ativo, comportamento idêntico ao anterior (seta e alternância).
- Sem binding: se há lista de sugestões não vazia (`Leso.b(List,…)`), `-40005` chama diretamente `Lmln.M(ZZLpfz;Z)V` no manager recebido pelo próprio evento — mesma chamada já revisada — e consome o evento. Novo toque fecha somente os favoritos abertos por esse fallback (`Lmln.y(ZZ)V`). Sem sugestões, o painel completo continua abrindo normalmente.
- Estado de sugestões é zerado a cada nova sessão de entrada.
- Cada motivo de abandono do caminho vinculado é registrado uma vez no logcat (`GboardPatches`/`Quick toolbar: …`), sem conteúdo do usuário. Para diagnosticar no aparelho: `adb logcat -s GboardPatches`.

Limite: no fallback não há seta desenhada no botão; o ícone de quadradinhos permanece e alterna favoritos/sugestões.

## Pesquisa do clipboard (PR C) — não implementada

Exige três bindings que só podem ser obtidos do APK 18.0.3: (1) a chamada que troca de `ClipboardKeyboard` para o teclado principal sem fechar a sessão do clipboard; (2) o container da janela do IME onde os resultados podem ficar acima do teclado; (3) o ciclo de vida de `ClipboardKeyboard.h()` nessa troca. O wrapper do PR A já fornece o mecanismo de redirecionamento isolado de `InputConnection` necessário para que a consulta não vá para o app. Implementar sem esses bindings seria adivinhar IDs/métodos.

## Validação

- Executado localmente com JDK 21 contra `android.jar` (API 34) e shims mínimos para `InputConnectionWrapper`, `Looper`, `Log`, `SystemClock` e o runner do Robolectric: `ImeEditJournalTest` (9), `JournalingInputConnectionTest` (6), `EditorActionSessionTest` existente, `QuickToolbar1803FallbackTest` (5) — todos passaram.
- `./gradlew` não resolve `app.morphe.patches:1.3.3` sem credencial do GitHub Packages; build Kotlin, Robolectric real, transformação do APK e `.mpp` **não** foram executados aqui. O patch Kotlin não foi compilado nesta sessão.
- Nenhum teste em aparelho. Critérios pendentes no S25 Ultra: Undo/Redo em ChatGPT, campos EditText comuns, navegadores e Termux; toolbar não abrindo o painel completo durante digitação; log `Undo journal inactive` ausente.

## Atualização após o relatório binário dirigido (16/10/2026)

- Confirmado no APK: `Loup;` (151 métodos) não declara `getCurrentInputConnection()`; o override é inserido. `Loqe.d()`, `Lopx.n()` e `Lfio` consultam `Loqa.getCurrentInputConnection()`; para o objeto `Loup` isso despacha para o override.
- `Loup.az()` compara `getCurrentInputConnection()` com `InputBinding.getConnection()` via `Objects.equals`. O wrapper agora é igual à conexão original (`equals`/`hashCode` delegados). Isso só cobre a ordem `Objects.equals(wrapper, raw)`; a ordem exata está pendente (pergunta abaixo).
- Custo: durante a composição de uma palavra, `setComposingText` não faz leitura; um par de snapshots por palavra (base ao iniciar, resultado ao confirmar/finalizar/interromper). Undo/Redo fecham a composição aberta antes de ler o editor. Limite: uma alteração externa durante a composição de uma palavra é atribuída a essa palavra.
- Contadores para validação no aparelho, ao fim de cada sessão de entrada: `adb logcat -s GboardPatches` mostra `Undo journal session: commit=… compose=… delete=… keyDown=… correction=… snapshots=…`. Só números. Se ChatGPT/EditText mostram contadores zerados durante a digitação, o Gboard não está passando pelo wrapper naquele caminho.

Pergunta pendente: **`Loup;->az()Z`** (`classes.dex`, `0x2f01e4`): ordem dos argumentos em `Objects.equals` (`pc=28`) e o que o resultado controla em `onStartInput` (`pc=83`) e `onStartInputView` (`pc=99`). Se a ordem for `Objects.equals(binding, current)`, o resultado fica `false` com o wrapper e é preciso tratar isso no patch.
