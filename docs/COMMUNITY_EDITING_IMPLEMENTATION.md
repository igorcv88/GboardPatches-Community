# Native editing actions — implementation and validation

Baseline: `6f8064acb366919213df2e1d6c1c21df24baad7c` (Gboard Patches 3.12.0).

The native AP contribution now registers six independent tokens, including `text_edit_undo` and `text_edit_redo`. Existing four stock resource IDs are preserved. Two bundled Material vector drawables are merged by the editing resource patch and resolved by resource name after installation, without guessing IDs in Gboard. Settings enumerate the same six actions; existing preference keys stay valid.

`EditorActionSession` first delegates host-owned history to the editor. For toolbar Paste/Cut, it captures a bounded before/after replacement and admits it only if unchanged prefix/suffix, selection and cursor prove the transaction. A reported native success with no observed change may use a local inverse replacement of that range. Local redo replays only the replacement actually undone; it does not consult stale host redo history. Native unexpected changes never receive a second edit.

Snapshots are in memory only, at most 32,768 UTF-16 units per state. Styled subsequences are retained for replacement. Incomplete extraction, active composing spans, passwords and editors requesting no personalized learning do not enter the local journal. No content is logged. Ending/replacing the input session clears snapshots and connection identity. The existing reviewed `Loup.onUpdateSelection(IIIIII)V` Android override (also used by Calculator) invalidates the journal on unexpected cursor/selection or composition changes. Long-press actions share the active session executor, with their previous native path retained when that session is unavailable.

## Validation

19 focused editing tests passed locally: existing session guards (6), replacement proof (3), and simulated editor Paste/Cut/Undo/Redo (10). These use the actual production session/transaction code and Android API classes on a JVM. The tests include acknowledged native no-ops, reversed selection, emojis, multiline text, session replacement, external edits and selection/composition invalidation. They are not device tests.

Full Gradle build and Robolectric settings tests remain unverified locally. Gradle 9.6.1 downloaded successfully after configuring the environment proxy, but plugin `app.morphe.patches:1.3.3` could not be resolved; the private Morphe package-registry credentials and Android build toolchain are absent here. No release workflow was dispatched.

## Physical validation still required

Use Gboard 18.0.3 on the S25 Ultra. Test typed Undo/Redo and toolbar Paste/Cut → Undo → Redo in EditText, WebView, Chrome, WhatsApp and ChatGPT. Confirm styled selections, span boundaries, composing text and asynchronous selection notifications. The fallback intentionally declines long/incomplete fields and external pastes it did not observe. Keyboard shortcuts (Ctrl+Z/Y) are not automatically retried after an acknowledged native call, because duplicating an unobserved asynchronous operation could undo unrelated text.

The descriptors provide direct actions, but dynamic disabled/grey presentation has not been mapped in the native AP API. Failed or unverifiable operations return safely; per-editor visual availability remains pending.
