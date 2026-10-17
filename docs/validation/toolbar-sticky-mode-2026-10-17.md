# Toolbar selected mode: PR #21 revision

Baseline: main 235dca4, PR head f5f402f. Reviewed Codex P2 discussion_r4304040535.

The old implementation had two independent states: fallbackShown and the new binding's
expanded flag. A valid rebind restored only the first. The next tap reopened favorites.
It also restored favorites after Undo/Redo only on the fallback path, and clearBinding()
closed the native row during controller replacement.

The runtime now owns favoritesSelected across both adapters. A replacement binding adopts
that mode before processing events; releasing view decoration does not hide the native row.
Explicit toggles and input-session reset cancel queued work. An empty candidate list does
not close user-selected tools. Candidate callbacks from an obsolete owner are ignored.
Without a controller, closing the selected mode calls native y(false,false) idempotently;
there is no assumption that a row is physically visible and no reopen in that tap.

Restoration is coalesced onto the main queue after native candidate callbacks (entry observes
the list; exit observes the rendered view) and successful history actions. No fixed 48 ms
sleep, polling, or strong capture of a manager/view/service is used. A new callback after a
later asynchronous redraw schedules another reconciliation. Native synchronous callbacks
are suppressed under applying, preventing recursion. At most one restoration is pending.

The underlying native M method remains reflective; a successful invocation is not proof
that pixels are visible. Device acceptance remains required.

## Additional native lifecycle evidence

In original Lmlh.M(View)V (DEX code offset 0x2affac), field y stores the incoming root,
field o is reset false, and j is the result of findViewById(2131427494), cast to Lmhm.
It is a View when non-null; the "not a view" log does not establish an intermediate wrapper.
A missing/unready native view is enough to explain that log. Reconciliation is therefore
also queued after controller replacement, even without a new candidate-list callback.
No view traversal or guessed wrapper field is added.

## Verification

ECJ Java 17 compilation of editing/toolbar/search runtime and tests against Android framework
classes passed. An isolated JUnit/Robolectric 4.14.1 harness ran the existing editing,
toolbar and clipboard-search suites plus five new runtime transition regressions: 122 tests
passed. Includes fallback-to-bound P2, detach/recreate, bound-to-fallback, empty candidates,
five sequential history actions, coalescing, explicit close, session reset, disable, and
out-of-order candidate owner callbacks. Pure Python tools suite: see execution report.

The repository Gradle wrapper was downloaded using the environment proxy, but project
configuration fails to resolve app.morphe.patches:1.3.3 from its configured repositories.
No Morphe build, patched APK, or physical-device validation is claimed. No Release workflow
was dispatched.

Performance: restoration has no timer and no per-frame loop; repeated callbacks before the
queue drains collapse to one native M invocation (asserted by the runtime regression).
This is an operation-count check, not measured device latency/framestats. On SM-S938B,
compare five Undo/Redo steps at 60/120 Hz, focus/rotation/floating keyboard and Frosted Glass,
including no candidates. Capture framestats and crashes/ANRs against 3.12.6 before acceptance.
