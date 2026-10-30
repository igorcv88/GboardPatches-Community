# Unified Undo/Redo for verified editor edits (Gboard 18.0.3)

## Cause

Typing/deletion used a multi-step `ImeEditJournal`, toolbar Paste/Cut used a separate
single `EditorEditTransaction`, and native clipboard cards used a third, asynchronously
verified transaction. `performContextMenuAction(undo)` can acknowledge a request without
changing the editor. Mixing these paths could lose earlier steps or fall through to an
unrelated host undo when the local chain was exhausted.

## Change

- Verified toolbar Paste/Cut and native text-card replacements now join the same bounded
  per-editor journal as typing, corrections, and deletion. Their insertion is one step,
  not merged with the preceding word. The previous span-aware single-transaction
  fallback is retained when a complete plain-text journal snapshot is unavailable.
- When the editor invokes Paste, Paste as plain text, or Cut through the wrapped
  `InputConnection`, its synchronous, observed change is recorded. A duplicate native
  clipboard callback does not register the same edit twice.
- Multi-character `commitText` calls (including clipboard suggestions when they use
  that method), and Ctrl+V/Ctrl+X/Shift+Insert key events, have their own edit boundary.
- A caret move alone no longer destroys a local chain. Full editor text must still
  match before a known replacement range is touched. New external text, incomplete
  extraction, private/password fields, composition conflicts, session changes, and
  edits over 32,768 UTF-16 units continue to fail closed.
- Once the journal owns a state, pressing Undo/Redo past either end of the chain returns
  false without calling the host undo manager, avoiding accidental unrelated changes.

## Automated regression coverage

- Typing → toolbar paste → typing → repeated Undo/Redo and exhaustion.
- Typing → native text card → typing → repeated Undo/Redo.
- Plain-text context-menu paste and cut with a changed selection.
- Direct multi-character `commitText`, short native-card paste, and duplicate event
  hooks: separate, single steps.
- An external text change still cannot replay a stale local edit.

## Device acceptance (S25 Ultra, Android 17)

1. In ChatGPT, WhatsApp, and a regular EditText, type two words, paste using the toolbar,
   type a suffix, then Undo back through suffix, paste, and words; Redo forward.
2. Repeat via the clipboard's native card, clipboard suggestion chip, contextual
   paste menu, paste-as-plain-text and text selection + Cut. Try a one-character clip,
   emoji, multiline text, and replacing a selection.
3. Move the caret between actions, switch fields/apps, use a password field, and try a
   long document. No old-field replay, private snapshot, or wrong-range replacement.
4. Confirm the toolbar stays available for successive Undo/Redo and the clipboard
   search, QWERTY routing and Frosted Glass still behave as before.

**Remaining limit:** a paste performed entirely by the host app without calling the
keyboard's wrapped connection or the reviewed native clipboard dispatcher cannot be
distinguished reliably from a programmatic app edit. That path remains host-owned;
no global editor interception or speculative full-document rewrite was added.

Local Gradle was blocked by access to the Morphe 1.3.3 GitHub Packages plugin; CI must
compile the Android extension and execute Robolectric tests. Physical input behavior is
not verified by unit tests.
