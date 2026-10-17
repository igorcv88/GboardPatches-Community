# Handoff execution report — 17 October 2026

Baseline main: 235dca4 (Morphe source points to 3.12.6).
Original Gboard APK SHA-256: 3099262121a201dcb2ae7e185bee6b2057287a5aeffae4be0e30f537e505e5b3.

| PR | Change | Verified head before this report |
| --- | --- | --- |
| https://github.com/igorcv88/GboardPatches-Community/pull/21 | Shared toolbar mode, Codex P2 rebind fix, callback-driven persistence | 5aa7ba282ef78d06f4c57c56e1b68116d300b769 |
| https://github.com/igorcv88/GboardPatches-Community/pull/22 | Confirm provider restoration before pasting a search result | 797d8ebf5a31cc8752021fdb43a332e80870df10 |
| https://github.com/igorcv88/GboardPatches-Community/pull/23 | Opt-in native Termux clipboard trace and binary findings | cf918ae2915ce707315ac89892fea33616fd68b3 |

The PRs are independent, based on main. No merge or release was performed.

## Executed validation

- ECJ Java 17 compilation against Android framework classes, using the existing cached
  JUnit 4.13.2 / Robolectric 4.14.1 dependencies and generated settings resource/catalog
  sources in a host harness. This is not an Android Gradle Plugin build.
- Toolbar branch: 122 tests passed; search branch: 116; Termux diagnostics branch: 117.
  These counts overlap and must not be summed.
- A detached temporary checkout received the three diffs without merging any branch:
  combined selected editing/toolbar/clipboard suites passed, **127 tests**, 17.545 seconds
  in the final run. This is host test duration, not runtime performance.
- Python tooling: `python -m unittest discover -s tools/tests -v`, **21 tests passed**.
- `git diff --check` passed for the independent changes and combined checkout.
- Exact binary methods/fields/access flags were inspected. In particular, Loup.aA is
  protected final, so the search host walks getDeclaredMethod/setAccessible across the
  service hierarchy; the regression uses an inherited protected test method.
- Gradle wrapper distribution was downloaded through the environment proxy. Project
  configuration failed resolving app.morphe.patches:1.3.3 from configured plugin registries.
  The repository requires MORPHE_PACKAGES_TOKEN for MorpheApp/registry; that secret is not
  available locally. The Release workflow was not invoked to work around this.

The full build command attempted locally uses the existing release job's validation tasks:

```sh
bash gradlew :extensions:extension:testDebugUnitTest \
  :extensions:calculator:testDebugUnitTest :patches:check :patches:buildAndroid \
  :patches:verifyReleaseBundleImport generatePatchesList --console=plain
```

There is no claim of full Kotlin/Java compilation, runtime ABI verification by Morphe,
patched APK creation, install, or physical-device validation. Hook instructions in #23
were reviewed statically, not executed by the patcher.

## Performance and remaining acceptance

Toolbar: pending restorations coalesce to one native invocation per queue drain; no fixed
48 ms timer, polling, or per-frame listener. The repeated-action regression checks invocation
counts. Search: one native provider confirmation per routing transition. Diagnostics: when
disabled, isLoggable checks only; enabled logging is deduplicated and contains numeric state.
No device latency, GPU, jank or crash/ANR comparison was measured; existing 3.12.1 captures
are historical evidence, not a controlled before/after comparison for these changes.

On SM-S938B, validate #21 with Smart Launcher and a normal EditText: one word and at least
five words, repeated Undo to exhaustion then Redo, one-tap return to suggestions, rebind,
focus/session changes, portrait/landscape/floating and 60/120 Hz. Compare Frosted Glass
framestats against 3.12.6 under identical conditions.

For Termux, #23's opt-in trace must locate the missing native stage before an eligibility
exception is written. The actual chip is **not implemented** by this diagnostic PR; there
is no terminal Undo/Redo or terminal input mutation. Sensitive/empty/incognito handling and
exactly-once Unicode/multiline paste remain future functional acceptance tests.

Composite search remains **disabled**. Native alphabet-to-clipboard return, field isolation,
Back navigation and touchable insets need device evidence before changing flags. Pin/delete
remain gated. The restore-confirmation fix does not establish those unrelated behaviors.
