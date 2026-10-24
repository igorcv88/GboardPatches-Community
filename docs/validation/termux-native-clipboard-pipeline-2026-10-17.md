# Termux native last-copy chip: binary findings and remaining gate

Baseline: main 235dca4; APK 18.0.3.954559732-release-arm64-v8a, SHA-256
3099262121a201dcb2ae7e185bee6b2057287a5aeffae4be0e30f537e505e5b3.
This is a diagnostic PR, not a completed Termux clipboard feature.

## Findings from the exact binary

| Stage | Binding and DEX code offset | Observed behavior |
| --- | --- | --- |
| Session | Lfjz.fn(Loru;EditorInfo;Z;Map;Lnve;)Z, 0x21756c | Passes native EditorInfo to Lfif.t; no EditorInfo override |
| Latest source | Lfjv.i(Lvvw;)V, 0x439c7c | Assigns source to Lfif.k and calls Lfif.k() |
| Creation | Lfif.x()Z, 0x2160b0 | Checks source/context/EditorInfo, disableAutoPaste and qqw.a; builds native qjv |
| Chips | Lfif.b(Context;Lvvw;EditorInfo;Lppe;)Lqjv;, 0x43754c | Chooses filter by input type; text defaults to Lfiu; no simple TYPE_NULL exclusion |
| Default text filter | Lfiu.a(EditorInfo;List;)List;, 0x371b34 | Deduplicates and uses Lfit input filters; TYPE_NULL has no numeric/date key filter |
| Display request | Lfif.j(Z)V, 0x437f50 → Lfif.v(Lpfz;Z)V, 0x438164 | Sends native qjr display notification with per-surface map |
| Holder selection | Lipn.dU(Lpyj;)V, 0x47bb60 | Selects native holder; defers when no suitable holder exists |
| Holder render | Lipl.k(EnumMap;)Z, 0x47c568 | Builds native view list and requests holder/header visibility through Lpga.p |

The input-type hypothesis alone does not explain the report. Potential points remaining
are native source/expiry state, disableAutoPaste, feature settings, input-context callbacks,
and holder/header visibility. None is identified as the device's root cause yet. No gate
is overridden on the strength of the package name alone.

## Opt-in trace added

Read-only exit observers on Lfif.t, k, j. Disabled by default; enable with:

```sh
su -c 'setprop log.tag.GboardClipTrace DEBUG'
logcat -s GboardClipTrace:D '*:S'
```

Reproduce: copy ordinary short text outside Termux, open the keyboard on the terminal,
then close/reopen it and make a second copy. Keep native clipboard settings unchanged.
Disable afterwards:

```sh
su -c 'setprop log.tag.GboardClipTrace INFO'
```

Output contains only `stage=<0..2> state=<integer>`. Stage 0=session prepared,
1=helper refreshed, 2=native display-request method returned. State is a bitset:

| Bit | Meaning |
| --- | --- |
| 1 | Termux TYPE_NULL |
| 2 | Termux visible-password + no-suggestions shape |
| 4 | IME_FLAG_NO_PERSONALIZED_LEARNING is set |
| 8 | Native helper source list nonempty |
| 16 | Native qjv constructed (helper.i non-null) |
| 32 | Native priority selected (helper.j non-null) |
| 64 | Raw native helper.q flag; not proof of rendered pixels |

The observer reads no clips, surrounding text, labels, URIs, commands, or timestamps;
it only checks whether the source list is empty. It does not fetch ClipboardManager,
change EditorInfo, route input, commit text, send Enter, or implement terminal Undo/Redo.
Only exact com.termux and the two terminal input shapes are traced. Even an incognito or
sensitive source yields no content in logs. Dedupe is reset at session prepare; objects
are never retained. Reflection failures are ignored without logging exception messages.

## Verification and next evidence

Java compilation and Robolectric tests cover bit output, malformed bindings, unchanged
native state and exclusion of other packages/password shapes. Hook signatures and p0
preservation were inspected in the original methods; Gradle/Morphe remains blocked resolving
app.morphe.patches:1.3.3. No patched APK or physical-device trace is claimed.

If source bit 8 is absent, inspect source loading/expiry rather than forcing display. If 8
is present without 16, inspect Lfif.x's disableAutoPaste/qqw.a/b branches. If 16 and 32 are
present without a visible chip, request a focused trace of Lipn.dU → Lipl.k → Lpga.p and
header attachment; do not alter global input-type handling. The binary is already available;
no need to request full DEX files.

The actual native Termux chip, sensitive/empty/incognito suppression and exactly-once paste
remain unimplemented/unverified. An exception must be localized to the clipboard gate once
the trace locates it. This PR intentionally makes no candidate or terminal behavior changes.

Performance: when disabled, one isLoggable check at native helper lifecycle exits. When
enabled, fixed field reads and state-change logs, no polling or per-frame listener. Device
latency/framestats were not measured.

## Complemento do trace físico de 24/10/2026

O primeiro arquivo enviado do S25 Ultra produziu apenas `stage=0` com estados `1`, `9` e `9`. O estado `9` prova fonte não vazia no `TYPE_NULL`, mas não houve chip construído (bit 16), prioridade escolhida (bit 32), nem eventos `stage=1/2` nesta coleta. Não prova ainda se `Lfif.x()` foi chamado.

O próximo patch acrescenta dois observadores sem alterar o resultado nativo de `Lfif.x()Z`:

| Stage | Significado |
| --- | --- |
| 3 | Entrada na tentativa de construir o candidato nativo (`Lfif.x`) |
| 4 | Saída normal de `Lfif.x` (sem registrar o valor de retorno) |

Interpretação: com fonte não vazia, ausência dos estágios 3/4 aponta para o gatilho/ciclo de vida anterior a `x`; presença dos estágios 3/4 sem bit 16 aponta para algum veto dentro da criação; presença do bit 16 sem exibição exige rastrear seleção de prioridade e holder. A sonda não conhece o motivo específico do veto e não autoriza desbloqueá-lo. Mantém os mesmos filtros de pacote, `TYPE_NULL`/visible-password, propriedade `log.tag.GboardClipTrace` e ausência de conteúdo sensível.

Após incorporar e compilar o patch, repetir exatamente o procedimento documentado, copiando texto curto antes de abrir Termux e depois durante uma sessão aberta.
