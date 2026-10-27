# Clipboard search window regression — S25 Ultra / Gboard 18.0.3

Evidence: GboardSearch_20261027_143633.zip (PID 13797), screenshots 83514/83516.
Package dev.jason.com.google.android.inputmethod.latin, Android 17. No clipboard or
query contents are included here. PR #27 is merged; this correction starts from
main 39fbba1, whose tree matches the previously reviewed search implementation.

## Exact cause of the reported exception

ClipboardNativeSearch1803.start() posts a 500 ms check. Its statement
`report("surface", new IllegalStateException("not visible"))` creates an exception
when `!shown.isShown() || shown.getHeight() == 0`. It does not throw it. The log
at 14:36:41.094 is produced by the patch itself, not a fatal Gboard exception.
No additional instrumentation is needed to recover its fixed message or origin.
Session.search() only initiates open() and reports a false result with a Toast.
The preceding return-controller fix lets execution reach this previously untested
layout path; it did not change this surface check or the cleanup implementation.

## Why reopening stays blank

At 14:36:40.525 the native back-to-prime action switches to prime. At .538 Gboard
activates its internal EditorInfo (internal=1); HEADER/BODY views are created.
At .094 the watchdog calls end(), which restores routing but does not return to
clipboard. Host.hideSurface() calls setCandidatesView(new View(ime)).

Before/after hierarchy, from 01_ime_antes.txt / 05_ime_depois.txt:

| Node | Before | After |
| --- | --- | --- |
| framework candidatesArea 0x102001d | 1440 x 0, INVISIBLE | 1440 x 2992, INVISIBLE |
| child installed by cleanup | absent | plain View, 1440 x 2992 |
| framework inputArea 0x102001e | 1440 x 2992 | 1440 x 0 |
| native InputView 0x7f0b0424 | 1440 x 2992 | 1440 x 0 |

INVISIBLE views still measure. The plain View with WRAP_CONTENT fills the available
height, leaving no space for native input. The same hierarchy is reused on reopen.
The search's candidates ancestor is INVISIBLE too: showing only candidatesArea
does not show fullscreenArea. The AOSP InputMethodService setCandidatesView sets
WRAP_CONTENT regardless of the supplied view's LayoutParams; setCandidatesViewShown
updates only mCandidatesFrame, while updateExtractFrameVisibility separately manages
mFullscreenArea. Reference:
https://android.googlesource.com/platform/frameworks/base/+/5f3c15d1842e8bb90267cd109b977baa276c66fb/core/java/android/inputmethodservice/InputMethodService.java
The actual Samsung hierarchy, not assumptions about that source branch, supplies
the node relationships above.

## Controlled composition and rollback

ClipboardSearchWindowSlot resolves the actual framework candidatesArea and validates
its relationship with inputArea. It rejects fullscreen extraction. It records the
original children, their LayoutParams and the two visibility states. The search is
attached with a bounded height and the candidates ancestor is shown. No InputView,
KeyboardViewHolder, clipboard RecyclerView or QWERTY child is removed/reparented.
setCandidatesView is not used. Cleanup removes only the owned search view, restores
the exact original children/layout/visibility, and leaves no placeholder. A failed
visibility check cancels search and returns to clipboard after routing restoration.
A partial show attempt also rolls back. Close/lifecycle operations are idempotent.

Routing remains the reviewed EditTextOnKeyboard -> Loup.Z(field,false) -> internal
provider, confirmed via aA(). Restore uses Z(null,false) before returning to clipboard
or committing a selected clip. The device trace already proves the provider transition;
it does not prove successful physical search layout or typing isolation.

## Invalid resource warnings

No package ID ff / 0xffffffff warnings already occur at 14:36:31.388, before the
14:36:40.449 tap. They repeat during ordinary native keyboard creation. They do not
establish the cause of this failure: the dump directly identifies the measuring
placeholder. New framework resource lookups reject IDs <= 0 before use. No -1
resource IDs are introduced. Full provenance of the pre-existing warnings is not
recoverable from their message-only log and is not asserted here.

## Validation

Automated regressions reproduce the invisible WRAP_CONTENT placeholder starving
inputArea, then verify bounded search height, ancestor visibility, preservation of
native InputView, 30 open/close cycles, exact prior-child restoration, partial-show
rollback, visibility-watchdog return and reopening. Existing filtering, Unicode,
Back/provider isolation, pick and clipboard/history checks remain required.

Physical acceptance remains pending: QWERTY below results; filtering and no query
text in the host app; Back; repeated open/close/hide/reopen; native clipboard controls;
clipboard contents/history preserved; search button skin on the actual theme.
