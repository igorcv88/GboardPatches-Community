# Composite search: verify provider restoration before paste

Baseline: main 235dca4 (3.12.6). This PR is independent of toolbar PR #21.

## Defect

Host.restoreRouting() discarded setProvider(null)'s result. Session.pick() swallowed restore
exceptions and proceeded to commitToEditor(), even if the search provider remained selected.
A clip could therefore be committed to the wrong provider while the caller reported success.
Z is void and can silently do nothing: original Loup.aC begins with an early return when C is
true. Merely not throwing is insufficient for either route direction.

## Binary proof

APK 18.0.3 SHA-256 3099262121a201dcb2ae7e185bee6b2057287a5aeffae4be0e30f537e505e5b3.
Loup.Z(Loqa;Z)V calls aC(provider,reset,true). aC(null,...) selects r and clears t's provider;
aC(field,...) selects t and calls t.n(field). Loup.aA()Z compares active v with editor r.
Thus aA()==true confirms the editor provider; false is required after routing to the field.
Unknown reflection results fail closed. This confirms provider selection, not native key
routing or device layout, which remain separate acceptance tests.

## Changes

Host confirms route and restore through aA(). Session consumes the boolean restore result.
Failed/throwing restore prevents paste and return-to-clipboard dispatch; surface teardown
still runs. A second pick on the closed session cannot commit. Opening failures also avoid
switching panels after a failed restore.

Results remain in setCandidatesView above the full native alphabet keyboard. No small keypad
or alternate inline result strip is introduced. All existing review flags remain false.
The unresolved alphabet-to-clipboard dispatch and portrait/landscape/floating insets remain
required before enabling this UI. Pin/delete are not enabled.

## Verification and limits

Two regression tests cover failed/throwing restoration and Z silently doing nothing.
The isolated Java/Robolectric suite is run against the actual changed runtime sources.
116 tests passed on this independent branch. The existing tools suite also passed (21 tests).
Full Gradle/Morphe configuration remains blocked resolving app.morphe.patches:1.3.3.
Physical-device routing, native clipboard return, patching, and APK 18.0.3 integration remain
unverified. If restore fails, no paste occurs; the native input lifecycle is still required
to clean up a provider which could not be restored. No release workflow was run.

Performance: one reflective aA() check per provider transition; no per-key or per-frame work.
No measured device latency or framestats comparison is claimed.
