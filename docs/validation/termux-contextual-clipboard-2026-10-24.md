# Termux latest-copy suggestion implementation

Status: r1 and r2 **failed physically**. The r2 device log stops at `private-session`. Revision r3 corrects a misidentified native flag; physical retest pending. No patched APK,
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
click listener **before publication**, builds `qju/qjv`, and publishes through the per-HEADER `qjr.c` map
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
incognito (the native EditorInfo privacy flag or the latched manual predicate), no-personalized-learning editors,
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


## Revision r2 after failed physical test

The first bundle did not show a chip on the S25 Ultra even with a fresh copy. This
supersedes host/CI success as acceptance evidence. The screen alone does not identify
which runtime guard or native rendering step failed.

Two concrete implementation gaps were found:

- Publication used `qjr.a(qjv,pfz)`, whose native listener chooses a suitable surface or
  caches a general pending candidate. Stock clipboard publication uses
  `qjr.c(Map<ppe,qjv>,pfz)`. r2 targets `ppe.a` (`HEADER`) explicitly through that path.
- A non-null chip plus matching source was treated as already displayed. Native dispatch
  can return while no view is attached or visible. r2 checks attachment, visibility and
  positive dimensions; a pending candidate is republished after actual header initialization
  or layout, with at most three dispatches per copy/session. No timer polling is used. Visibility also requires a nonempty global visible rectangle,
  a visible window and nonzero alpha through the ancestor chain; attached children fully
  clipped by a collapsed header do not count as visible.

A hook at entry to the verified `Leso.y(View,ppe)` initialization captures the HEADER root,
including sessions where no prediction list arrives. It does not modify this root, the
native toolbar or arrow, EditorInfo, or terminal input policy. A temporary layout observer
is removed on success, expiration or session cleanup; after dispatch exhaustion it only
observes visibility and cannot send more notifications. Candidate layout can still report
later visibility after dispatch retries have stopped. The original paste/privacy guards
are unchanged.

These are verified code defects, **not proof of the specific cause on the device**.
The revised bundle must pass a fresh physical test before acceptance.

r2 host verification: 37 tests passed (14 adapter, 8 policy/ticket, 15 existing toolbar),
including a candidate deferred until the real HEADER root arrives, bounded dispatch when
no holder accepts it, cleanup on leaving the session, and exclusion of clipboard text from
logs. ABI check against the exact original APK passed for the header hook and per-surface
publication overload. Full CI runs again for the revision.

The review bundle now has a run-specific version (`0.0.0-review.<run number>`) so successive
imports can be distinguished. State logs use `GboardTermuxClip` and the prefix `r2`:
`session-bound`, `waiting-session-helper`, `editor-mismatch`, `private-session`,
`disabled-by-editor`, `device-not-unlocked`, `source-empty`, `sensitive-source`,
`source-policy-blocked`, `chip-built`, `requested-header`, `pending-holder`, `visible`, or
`binding-failed:<exception class>`. No text, labels, raw timestamps, paths, or exception
messages are logged. The names distinguish attempted delivery from observed visibility.

If the r2 physical test still fails, its existing state logs can be collected without
another instrumented APK or another clipboard-availability investigation:

```sh
su -c 'logcat -d -v threadtime -s GboardTermuxClip:I "*:S"' > /sdcard/Download/gboard_termux_r2.txt
```


## Revision r3: incorrect privacy guard identified

The physical r2 log supplied on 24 October contains `session-bound` followed by
`private-session` on every attempt, and never reaches `chip-built`. This establishes
that the implementation's privacy gate blocked candidate construction before display;
it does not by itself distinguish the three conditions combined under that state.

Binary inspection identified a definite error in that gate: `Lfif.o` is **not** an
incognito flag. In the exact original APK, `Lnvk.I(Lnvq;Lnve;Map)Z` obtains two EditorInfo
objects through `Lnvl.dn()` and `Lnvl.S()`, compares their **identity**, and passes that
boolean as the fourth argument to `Lnvo`'s constructor. `Lnvo.a()` forwards it to
`Lnvs.fn`; `Lfjz.fn` passes it to `Lfif.t`, which stores it in `Lfif.o`.

The observed producer is:

```text
v3 = Lnvl.dn()  // EditorInfo
v9 = Lnvl.S()   // EditorInfo
v4 = (v3 == v9)
new Lnvo(extension, inputContext, v3, v4, map, callback)
```

`Lfie.a(Loqf)` reads this flag for the first InputContextNotification payload, while
`Lfie.b(Loqf)` reads its inverse for the second payload. A normal input session can have
this flag true. r1/r2 incorrectly rejected such sessions as private. Their adapter test
double defaulted the flag to false, hiding the false positive; it now defaults to true.

The real stock incognito predicate is `Leqy.G()Z`: read the active EditorInfo's
`imeOptions` and test `0x01000000` (`IME_FLAG_NO_PERSONALIZED_LEARNING`). r3 preserves that
check on **both** EditorInfo copies and independently preserves the extension's latched
manual-incognito predicate. It removes only the incorrect use of `Lfif.o`. Sensitive
source suppression, locked-device readiness, disableAutoPaste, terminal input shape,
connection/session checks and multiline safeguards are unchanged.

r3 logs separate `private-editor` from `private-manual-incognito`, and use prefix `r3`.
No copied content is logged. A real private session remains blocked, including manual
incognito that permits browsing the clipboard panel: this automatic suggestion stays off.

41 local tests passed: 18 adapter, 8 policy/ticket and 15 toolbar tests. New regression
coverage includes normal context-selection=true with a successful single paste, manual
incognito before/after publication, manual incognito with clipboard browsing permitted,
and a private module EditorInfo copy. All existing normal-session adapter tests now use
context-selection=true. The standalone harness uses the real session-policy class plus
a minimal runtime stub whose predicate matches production; full CI runs these tests with
the real production manual-incognito runtime. The exact-APK validator also verifies the
activation producer and the real stock incognito predicate.

The provided log and native producer explain a concrete false-positive privacy condition.
This is a functional correction in PR #26, not a diagnostic-only PR. Physical success of
r3 is still unclaimed until the device test.
