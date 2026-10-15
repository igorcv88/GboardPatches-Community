# Contextual toolbar and native clipboard search — foundations

These components are tested, but are not connected to the Gboard interface yet. No additional toolbar or clipboard UI is installed by this PR.

`QuickToolbarState` implements stock / suggestions / quick tools transitions. Active candidates switch the left button between favorites and candidates; absent candidates, context loss, detach and feature-off reset to the stock action. The adapter must feed actual candidate state, not infer it from an empty editor string. Favorites must come from the existing AP catalog.

`ClipboardSearchMatcher` performs bounded-query, accent/case-insensitive multiword searches over full text in 2,048-unit chunks. It handles term and surrogate boundaries without constructing a normalized copy of every 200k clip. `ClipboardSearchController` debounces 150 ms on one cancellable worker, invalidates stale results on query/snapshot/session changes, and delivers IDs to the owner-thread executor. Sensitive projections discard their text; matching searches exclude them. Clearing the query restores their IDs for stock masked rendering, together with nontext items. Queries are not persisted.

`ClipboardSearchPresentation` filters the current UI-owned snapshot, preserving the exact original model objects, IDs and order. It emits only nonempty section headers and derives recent/pinned counts. It has no database/pruning API and never updates timestamps or TTL. The worker never receives Gboard model/view objects.

## Validation and missing integration

12 focused JVM tests passed: state transitions (2), matcher (5), worker/cancellation and 0/1/100/500 clips (4), presentation identity/headers/counts (1). No physical UI/performance claim is made.

The previously uploaded `base.apk` was inspected through its binary manifest: it is **18.5.1.993608717-beta-arm64-v8a**, not 18.0.3. Its classes cannot authorize the two missing 18.0.3 UI bindings. The fork contains reviewed AP and clipboard data hooks, but no binary/resource evidence for the candidate launcher click/state view or clipboard header/focus routing. No 18.5.1 class was transplanted into the 18.0.3 bindings.

To finish, inspect the actual 18.0.3 binary and bind the launcher/candidate controller plus clipboard header/lifecycle and search-key routing. For clipboard, snapshot source IDs/text off the UI hot path, apply presentation results on the adapter owner thread, preserve stock callbacks, restore the original InputConnection on exit, and close the worker on panel detach. Bind search input before enabling the feature, so typing a query cannot leak into the host editor. Verify view measurement, orientation, theme tint/Frosted Glass, back/cancel, sensitive/incognito mode, and trim/live database updates while filtering. The corresponding UI switches are deliberately not exposed until these adapters exist.

Rambler insertion mode and 18.5.1 port were not changed.


## Review corrections, 2026-10-15

The caseless key is now NFKD plus full ROOT uppercase and mark removal, without context-sensitive String lowercase. Greek final sigma, sharp-s expansions and decomposed accents are tested on both sides of the 2,048-unit chunk boundary. Matching no longer depends on the final-sigma lowercase rule at that boundary.

A production `forImeMainThread()` factory posts results through an Android main-Looper Handler. Tests can still inject executors. This factory is not a UI binding and no panel/search field is exposed by this PR.

`tools/benchmarks/ClipboardSearchBenchmark.java` measured five full absent-query scans of 500 distinct 200,000-unit strings on this host JVM, with a 512 MB heap limit: p50 398.0 ms, p95 (five-sample maximum) 525.7 ms. This excludes debounce, database projection creation, Android rendering and touch latency. It is not device p95 or a heap-retention measurement. The controller retains immutable string references and the matcher only materializes normalized chunks; DB snapshot construction, lifetime under live Gboard updates and device memory still require the real adapter and APK.

Always project IDs back onto the current original model snapshot; do not feed already-filtered presentation rows into another search. Preserve the existing 3–8 favorites/overflow policy rather than forcing six edit actions plus clipboard into six slots. These visual/focus/retention contracts remain unvalidated until the native view adapter exists.
