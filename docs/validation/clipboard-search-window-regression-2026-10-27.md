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


## Native control appearance (separate stage)

The search host remains unstyled and uses the Edit control's background and native
press foreground. Foreground ConstantState clones previously lost runtime alpha,
filter and tint: a transparent white highlight could cover the entire lens as an
opaque circle. Copies now retain those properties, level and search-owned state.
Native content/peer alpha is included in copied backgrounds. Native originals are
never modified. The new graphics test draws the whole ImageButton, not just its
background: it requires a translucent backdrop and visible white lens with a
transparent white native foreground. This addresses a concrete cloning defect;
exact visual equality on the user's custom theme still needs the device retest.

## Layout and appearance round (search functional on device)

Report: search opens, QWERTY and results appear, but the panel sits at the window top,
far above the keyboard; it looks provisional (opaque black, blue underline, plain list);
the header search action is still a solid white disc without its lens. No screenshots are
stored here.

### Gap between panel and keyboard

Cause, from the dump above: framework inputArea and Gboard InputView span the whole
window (1440 x 2992) and draw the keyboard at their bottom; candidatesArea is the first
row of the window. A bounded surface in that row is at y=0 regardless of keyboard height.

ClipboardSearchWindowSlot now anchors the surface to the native keyboard root, found by
the theme tags `.keyboard-base-area.keyboard-outline` / `.keyboard-base-area` (the same
roots Frosted Glass and Rounded Keyboard use). On every pre-draw it sets the surface's
top margin to the space between the area top and the keyboard top, limits its height to
that space and copies the keyboard's horizontal extent. inputArea then measures exactly to
the keyboard; slot and keyboard are adjacent rows of the IME layout. No pixel constant
is involved, and the computation is a fixed point for bottom- or top-aligned keyboards.
Without a keyboard root the bounded slot is left exactly as before. The first frame is
withheld until anchored (at most three frames) so the surface never flashes at the top.
Margins live on the owned surface's LayoutParams and disappear with it on close.

### Panel left above the clipboard

The session was only closed by its back button, Back, picks and input lifecycle. Returning
to the clipboard by a native path (toolbar clipboard key, keyboard switch) re-activated
ClipboardKeyboard.e() with the search still OPEN: routing stayed on the internal field and
the search row stayed above the clipboard. ClipboardSearch1803Runtime.opened() now calls
ClipboardNativeSearch1803.clipboardShown(), which ends an open search (restore routing,
remove the surface, no keyboard switch). It is inert while opening and after our own
cancel, which already marked the session closed before returning to the clipboard.

### Search action rendered as a white disc

All previous builds rebuilt the native background from ConstantState and replayed runtime
properties one by one (tint list, tint mode, color filter, alpha, level, foreground). The
native disc is a white asset made translucent gray at runtime. Any property the replay
missed reverted to the white asset. Concretely, the copy was put in the search button's
state set (`copy.setState(search.getDrawableState())`), so a state-dependent native tint
was evaluated for a state the native key has and the search button lacks; and a cloned
native highlight foreground could cover the lens. The lens is white too, so the result was
a white disc with an invisible white lens. Each fix addressed one replayed property, and
the next missing one produced the same symptom.

The disc is now a plain oval of the color the live native background renders in its own
state (drawn into a bitmap; native drawables are not mutated), at the rendered bounds
inside the key, multiplied by the native view alpha. Press feedback is the search's own
translucent overlay; no native foreground is cloned. The lens color and size come from
the rendered native Edit glyph (tint, filter and vector tint included). A disc the lens
cannot be distinguished on is rejected in favour of a translucent disc of the glyph color.

### Native appearance of the search surface

ClipboardSearchStyle is captured from the visible native clipboard before switching:
backdrop composited from the backgrounds between inputArea and the list (window-level
backgrounds are shared with the slot and not repeated), first history card's rendered
color, corner radius, padding, spacing, text color/size/typeface/max lines, and the header
control color/icon/size from the search action. The surface uses them for its background,
result cards (with pressed state), a header pill containing the field (no platform
underline; cursor and hint in the icon color) and a round back control with an arrow
glyph. Values that cannot be read fall back to the IME theme.

### Validation in the repository

- Robolectric (SDK 26 and 33): 69 executions of the slot, search host, session, surface,
  style and search-action suites pass, including new checks for: surface bottom equal to
  keyboard top, following keyboard height changes and limited height; horizontal extent;
  unchanged behaviour without a keyboard root; clipboard re-activation ending search
  without a second keyboard switch; state-dependent native tint read in the key's own
  state; rejecting a white disc under a white lens; glyph-sized lens; captured backdrop,
  card, radius, spacing and text; surface without underline or opaque theme background.
- 21 Python tooling tests pass.

### Pending on the device

Gap closed at the actual keyboard height and orientation; appearance on the user's theme
with Frosted Glass; gray disc with visible lens; no row left after native return to the
clipboard; repeated open/close/hide/reopen; top-row key previews (InputView now ends at
the keyboard top while search is open, as it does with Frosted Glass's bounded surface).
