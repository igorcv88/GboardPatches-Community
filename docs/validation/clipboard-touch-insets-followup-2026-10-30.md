# Clipboard search touch-region follow-up (30 October 2026)

Target: Galaxy S25 Ultra / Android 17 / Gboard 18.0.3.954559732.
This follow-up is included in PR #30 with the independent Undo/Redo work.

## Reproduction and evidence

The search opens, filters clips, and anchors a 1440x720 results panel at
`Rect(0, 1106 - 1440, 1826)`. The user can type, but touches on result cards,
the list and the back arrow go through to the editor behind the IME; neither
selection nor scrolling works. In the installed build's opt-in log:

- `Clipboard native search r12: open-requested`, `anchored panel=...`, and
  `scroll first=0 ... total=...` are present (the latter follows filtering).
- No `panel action=0`, `list action=0`, `back action=0` is observed.
- No `native mode=...` / `union mode=...` is observed after anchoring.

The lack of insets diagnostics does **not** alone prove that the bytecode hook
was never invoked: a guard might also have returned early. It does prove that
the existing search-aware union was not logged for the anchored panel.

## Change

1. Patch the nearest declared `onComputeInsets(Insets)` implementation in the
   target's LatinIME → Leqy → Loup dispatch chain, instead of always assuming
   the Loup implementation is dispatched. Keep native insets and union only the
   bounded panel during an open search.
2. After the panel becomes visible and anchored, request a fresh root layout
   on subsequent animation frames, stopping once its touch-region calculation
   is observed (max four attempts). A request during `OnPreDraw` alone may be
   coalesced with the current traversal. No perpetual invalidation loop.
3. Extend opt-in diagnostics with `insets-enter`, `touch-region-request`,
   `touch-region-computed-for-panel` and
   `touch-region-not-computed-after-retries`. These contain no clip text,
   search term, clipboard IDs, or key contents.

This is a code-side correction and diagnostic improvement. Actual WindowManager
delivery and gesture routing require device acceptance after building PR #30.

## Device acceptance after building PR #30

Run this in Termux to enable the opt-in log:

```sh
su -c 'setprop log.tag.ClipboardSearchTouch DEBUG'
```

Next, paste the **single command** below. It stops on a terminal prompt until
Enter is pressed, unlike pasting a separate `read` line after a whole command
block. While it is waiting, open clipboard search; type a query with multiple
results; scroll and tap a result; reopen, then tap Back. Return to Termux and
press Enter to capture the results.

```sh
bash -c 'printf "\nRepita agora os testes no Gboard e volte ao Termux.\n"; read -r -p "Pressione Enter para coletar os logs: " _ < /dev/tty; su -c "logcat -d -b main -v time -s ClipboardSearchTouch:D GboardPatches:W" | tail -240; su -c "setprop log.tag.ClipboardSearchTouch WARN"'
```

Expected: `anchored panel`, `insets-enter valid=true panel=true shown=true`,
`native mode=`, `union mode=`, `touch-region-computed-for-panel` and genuine
`panel/list/back action=0` when touched. The original editor must not receive
touches on the panel. Search and normal QWERTY input must stay functional.

If `touch-region-not-computed-after-retries` appears, investigate IME root
`OnComputeInternalInsets` dispatch/window traversal on the actual firmware.
If the union log appears without panel touch events, inspect sibling view
interception and the WindowManager's published touchable region; do not add a
full-screen hitbox over the host app.
