# Toolbar transitions, pinned ordering and clipboard search — 18.0.3

Baseline: main dbe99fe. Physical report: 82622.mp4 (26 October 2026), S25 Ultra.
The video shows the manually selected toolbar remaining after typing resumes; the
user also reports candidate/toolbar flashing during Undo/Redo, pinned items below
recent items, and no clipboard search affordance.

## Toolbar

Previously each candidate callback posted native `mln.M` to restore favorites.
Candidates could request their surface first, producing a transient replacement.
Now `eso.C()`'s header-only surface request is skipped while the same candidate
owner has user-selected tools. `eso.b()` continues binding native candidate data.
No prediction text, typing policy or EditorInfo is changed. History actions do not
post restoration anymore. Controller replacement still has bounded reconciliation.
Native printable key events release the selected row before stock handling, so
predictions can return. Access point events keep tools selected. Explicit toggles,
disable and input-session reset still invalidate pending reconciliation.

A second permanent row was evaluated but is not implemented. Native favorites
and prediction requests arbitrate the same HEADER surface. This change fixes
that arbitration rather than changing keyboard height/layout or reparenting views.

## Pinned first

Native fjk.n is Recent header/items, Pinned header/items, Special header/items.
Multiple native operations depend on that order, including R counts, F expiry
trimming, I insertion, Q replacement and bulk pin/unpin. Moving this list would
corrupt those assumptions. It remains unchanged.

A cached permutation translates visual getItemViewType and bind positions to
native positions: Pinned, Recent, Special (stable order inside sections). Only
reviewed clipboard callers of kr.b() translate holder positions for menus,
selection and swipe deletion. Native canonical granular notifications become one
coalesced full visual refresh. Other adapters in shared synthetic listeners keep
their original notifications. Scroll targets and sticky labels use the same
translation. No clipboard database row, timestamp, ID or expiry policy is changed.

## Search

Search had been compiled but permanently hidden by four false constants.
The native path is now enabled and opening checks actual prerequisites instead:
- The header search button uses the existing actions container 0x7f0b0527.
- `mlh.h` is the stock descriptor catalog. The `clipboard` descriptor's
  `mic.e(pmy.a).b()` supplies the native primary action used by `mln.K`.
- The clipboard header's own back-to-prime action (SoftKeyView.h -> pnu) opens
  alphabet input through nur.d and Keyboard.x.M.
- Results use the IME framework candidates area; query uses EditTextOnKeyboard
  and Loup.Z(field,false), with provider selection checked via aA(). Query learning
  and fullscreen extraction are disabled. No query text is logged or committed
  to the host editor.
- Cancel/Back restores Z(null,false), confirms editor routing and dispatches the
  captured stock clipboard action. Pick restores routing before one commit.
- Existing failed/throwing restore, stale callback and lifecycle cleanup tests
  remain. Missing actions/provider/connection abort opening. Sensitive clips are
  excluded before text extraction. Delete in search results stays disabled;
  native panel pin/delete/selection remain available.

## Evidence and limits

Exact APK SHA-256: 3099262121a201dcb2ae7e185bee6b2057287a5aeffae4be0e30f537e505e5b3.
The reviewed methods, register shapes, native source list and action signatures
were re-read from this APK. Tests cover toolbar ownership/typing transitions,
permutation/bijection, canonical action indices, cache invalidation, coalesced
notifications, independent panels, search attachment and native return actions.
CI result and review bundle are recorded in the PR.

Physical acceptance remains pending for this revision. Check repeated Undo/Redo
without alternating surfaces, typing after tools, clipboard open/close, pin/unpin
and single/bulk delete with distinct identifiable clips, search accent/case/long
text/Unicode, query isolation (including Termux), close/Back/pick, focus changes,
portrait/landscape/floating and Frosted Glass. Recheck the already accepted Termux
clipboard suggestion. The previous r3 success proves that feature, not these changes.

## Device feedback on review.8 and revision 9

The user confirmed that typing after tools now returns to suggestions. Two failures
remain on device: Undo hides the toolbar leaving an empty header, and the search
icon does not activate search, is separated from the right action cluster and uses
an inappropriate bitmap icon.

Revision 9 removes the incomplete eso.C suppression. A transient pre-draw observer
maintains the selected native row after native arbitration, before a blank/candidate
frame is painted. It reuses mln.M only when the actual row is not visible; it cancels
at most two draws if the native holder refuses. Typing/toggle/session teardown remove
the observer. Clipboard and search retain their own header. No timer or frame polling
runs while suggestions are selected.

The search action gets a vector lens, theme foreground and the measured bounds of
the adjacent visible native key. Touches starting in its visible bounds are handled
at SoftKeyboardView.dispatchTouchEvent before the native key graph can swallow an
ordinary ImageButton click. Outside gestures remain stock; cancel/drag/multitouch
cannot activate search. Accessibility click still invokes the same single listener.
The IME is captured from onStartInputView rather than requiring the clipboard's
themed/application Context to be an InputMethodService. Missing prerequisites now
produce a visible failure message and fixed state names, never clip/query text.

New regressions simulate a native parent swallowing child clicks, vector/alignment,
cancelled gestures, actual row hiding after repeated history actions, drawing refusal,
clipboard ownership, and an application Context separate from the bound service.
CI, transformation of the original APK and physical validation are separate checks.
