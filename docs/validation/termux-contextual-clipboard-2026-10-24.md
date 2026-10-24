# Termux latest-copy suggestion implementation

Status: implementation PR; **physical S25 Ultra validation pending**. No patched APK,
Morphe transformation, successful device display, or terminal paste is claimed here.

## Strategy

The physical trace already proves a populated native source. The original binary also
proves `Lfif.t` invokes `x()` directly. Further instrumentation is unnecessary for this
implementation. The native creation path depends on source expiry, the themed input
context, `disableAutoPaste`, device-unlocked readiness and per-editor filters. The trace
does not identify which veto fired; this PR does not pretend to have identified it.

Use a contextual fallback within the native clipboard helper, only for exact
`com.termux` + `TYPE_NULL`. Its `x()` entry delegates to the extension and consumes
that branch, so stock and fallback cannot both publish/insert. Every other package and
input shape retains the original bytecode path, EditorInfo and InputConnection behavior.
Malformed/unrecognized EditorInfo also retains the stock path.

The fallback reads `Lfif.k[0]` (no new clipboard manager, history, polling, database or
logs), renders with the reviewed native `Lfif.y` text-chip layout, replaces its stock
click listener **before publication**, builds `qju/qjv`, and publishes through `qjr.a`
into the existing proactive suggestion holder. It adds no view bar, permanent button,
toolbar replacement or arrow event handler. The fallback does not rely on a prediction
engine or on `oru.a()` returning a themed context. It uses the native module theme context when available, with the active IME context as fallback.

The native helper remains responsible for delivering new sources. Service start covers
copies received before the keyboard opens; helper refresh covers later copies. End/hide
and helper close cancel the chip and pending confirmation. Source identity and a revision
ticket invalidate stale taps and confirmations. A connection/editor/source/privacy
recheck runs immediately before insertion. The ticket is consumed before one
`commitText(text, 1)` invocation; a false result or exception never causes a retry.

Native last-copy expiration (two minutes) is preserved and enforced by one cancellable
expiry callback. Empty clips, clips over one million UTF-16 code units, sensitive clips,
incognito (native helper flag or manual predicate), no-personalized-learning editors,
`disableAutoPaste`, locked-device readiness and unavailable bindings are suppressed.
The incognito exception allowing manual clipboard browsing does not enable this chip.
C0/C1 controls and DEL are refused, except CR/LF/TAB. CR/LF/TAB and Unicode line separators
require a separate confirmation warning that the complete text may execute commands.
Cancellation, window-token failure, editor/connection change, new copy or expiry cannot
insert. Confirming preserves the original string, including Unicode and trailing newlines.
No synthetic Enter, native click dispatch or extra paste path is used.

## Local evidence

- Exact original APK SHA-256:
  `3099262121a201dcb2ae7e185bee6b2057287a5aeffae4be0e30f537e505e5b3`.
- `tools/validate_termux_clipboard_bindings.py` verifies this hash, all reflected native
  methods/fields and the 13-register `x()` shape. Requires the optional `androguard`
  package and the original APK as argument.
- New production Java compiles against Android 14 API classes using ECJ Java 17.
- 8 policy/ticket JUnit tests and 11 Robolectric API 33 adapter tests pass locally.
  The adapter tests use small native ABI doubles, plus a stub of the manual-incognito
  predicate (default false) in the standalone local harness. They do not execute Gboard's
  notification center or the real manual-incognito integration. Gradle uses the real
  production predicate; pure policy tests independently cover incognito suppression.
- 15 existing toolbar fallback/binding tests pass with current toolbar sources and the
  existing standalone harness's settings/editing support classes. Total: 34 tests passed.
- Full Gradle tests/build were attempted but the wrapper cannot download Gradle 9.6.1
  through Java's network path in this environment. Patch transformation, D8/ABI inventory,
  registry contract execution and full integration build could not run locally.
- GitHub Actions run `40108729958` passed the full extension/calculator tests, patch checks,
  Android bundle build and Morphe release-bundle import check (before the final theme-context
  refinement). It uploaded the review `.mpp` and test reports. The final commit is revalidated
  by the same workflow. No original-APK transformation or physical validation is implied.

## Physical acceptance matrix (all pending)

The new `Validate patch bundle` PR workflow runs the full checks and uploads a review `.mpp`
without publishing a release. Its result is pending at PR creation.

Use the existing Gboard patch workflow with `Toolbar Editing Buttons` included, the exact
18.0.3 original, and this branch's `.mpp`. Do not replace/uninstall Termux or reset its data.
Test with a harmless copy; disable the old diagnostic property if it remains enabled.

| Case | Required observation |
| --- | --- |
| Short text copied before opening Termux | Chip appears in existing suggestion region; tap inserts exact string |
| New text copied with session active | Chip refreshes; old view cannot insert |
| Leave Termux and return | No chip leaks to the other app; latest eligible copy returns |
| Rapid double tap | Exactly one insertion |
| Unicode/emoji (`olá 🐈‍⬛ 日本語`) | Exact string arrives, no duplication or truncation |
| Multiline (`printf one` + LF + `printf two` + LF) | First tap opens warning and inserts nothing; Cancel inserts nothing; explicit Paste inserts once |
| Sensitive Android copy, native/manual incognito, private editor | No chip and no insertion from a stale tap |
| Ordinary editors and toolbar/arrow | Original suggestions, favorites toggle and arrow behavior remain unchanged |
| Expiry/clear/source becomes unavailable | Chip disappears; no stale paste or confirmation |

The physical check must additionally confirm native holder selection, native chip theme,
IME attached-dialog behavior on Android 17/One UI 9 and Termux's actual `commitText`
semantics. A successful host test is not evidence these device checks passed.
