package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.text.Spanned;
import android.text.SpannedString;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** All calls run on the IME thread. Snapshots are bounded, transient and never logged. */
public final class EditorActionSession {
    public interface Editor {
        InputConnection connection();
        default boolean allowSnapshots() { return false; }
    }

    private WeakReference<Editor> active = new WeakReference<>(null);
    private WeakReference<InputConnection> journalConnection = new WeakReference<>(null);
    private long generation;
    private EditorEditTransaction transaction;
    private Snapshot original, pasted;
    private boolean localUndo;
    private boolean dispatching;
    private PendingPaste pendingPaste;
    private long pasteSequence;
    private boolean composing;
    private final List<int[]> pendingOwnSelections = new ArrayList<>();

    public void begin(Editor editor) {
        end();
        active = new WeakReference<>(editor);
    }

    public void end() {
        generation++;
        active.clear();
        clearHistory();
        composing = false;
    }

    private void clearHistory() {
        pendingPaste = null;
        pendingOwnSelections.clear();
        transaction = null;
        localUndo = false;
        original = pasted = null;
        journalConnection.clear();
    }

    public void onSelectionUpdated(int start, int end, int candidateStart, int candidateEnd) {
        composing = candidateStart >= 0 || candidateEnd >= 0;
        if (pendingPaste != null) {
            if (composing) clearHistory();
            else {
                PendingPaste pending = pendingPaste;
                observeNativePaste();
                if (pendingPaste == pending && (start != pending.before.start || end != pending.before.end)) {
                    clearHistory();
                }
            }
        }
        if (transaction == null || dispatching) return;
        Snapshot expected = transaction.undone ? original : pasted;
        if (candidateStart >= 0 || candidateEnd >= 0) { clearHistory(); return; }
        if (expected.start == start && expected.end == end) {
            pendingOwnSelections.clear();
            return;
        }
        // A late intermediate callback is tolerated only if it was emitted by our own
        // replacement AND a fresh read proves the current editor is already at the final state.
        for (int index = 0; index < pendingOwnSelections.size(); index++) {
            int[] own = pendingOwnSelections.get(index);
            if (own[0] != start || own[1] != end) continue;
            Editor editor = active.get();
            long expectedGeneration = generation;
            try {
                InputConnection connection = editor == null ? null : editor.connection();
                Snapshot live = connection == null ? null : snapshot(connection);
                if (connection != null && journalConnection.get() == connection
                        && current(editor, connection, expectedGeneration) && expected.same(live)) {
                    pendingOwnSelections.subList(0, index + 1).clear();
                    return;
                }
            } catch (Throwable ignored) { /* Invalidated below. */ }
            break;
        }
        clearHistory();
    }

    public boolean execute(EditorAction action) {
        observeNativePaste();
        pendingPaste = null;
        Editor editor = active.get();
        long expected = generation;
        if (editor == null || action == null || dispatching) return false;
        dispatching = true;
        try {
            InputConnection connection = editor.connection();
            if (connection == null) return false;
            if (action == EditorAction.COPY || action == EditorAction.CUT) {
                CharSequence selected = connection.getSelectedText(0);
                if (selected == null || selected.length() == 0) return false;
            }
            if (!current(editor, connection, expected)) return false;
            boolean needsSnapshot = transaction != null || action == EditorAction.PASTE
                    || action == EditorAction.CUT || action == EditorAction.UNDO || action == EditorAction.REDO;
            Snapshot before = needsSnapshot && editor.allowSnapshots() ? snapshot(connection) : null;
            if (!current(editor, connection, expected)) return false;
            if (transaction != null && (journalConnection.get() != connection || before == null
                    || !transaction.matches(before.text.toString(), before.start, before.end)
                    || !(transaction.undone ? original : pasted).same(before))) {
                clearHistory();
            }
            boolean historyAction = action == EditorAction.UNDO || action == EditorAction.REDO;
            if (historyAction) {
                // Keyboard-typed history is independent of the host's undo support.
                Boolean journaled = journalHistory(editor, connection, expected, action == EditorAction.UNDO);
                if (journaled != null) return journaled;
                // Once this journal has undone a replacement, only redo that exact transaction.
                if (transaction != null) {
                    if ((action == EditorAction.UNDO) == transaction.undone) return false;
                    return reverse(editor, connection, expected, before);
                }
                // Operations outside the observed local history remain host-owned.
                boolean accepted = connection.performContextMenuAction(action.contextMenuId);
                Snapshot after = before == null ? null : snapshot(connection);
                return current(editor, connection, expected) && (before == null ? accepted
                        : after != null && !before.same(after));
            }
            if (action != EditorAction.COPY) clearHistory();
            boolean accepted = connection.performContextMenuAction(action.contextMenuId);
            if (before != null && (action == EditorAction.PASTE || action == EditorAction.CUT)) {
                Snapshot after = snapshot(connection);
                if (current(editor, connection, expected) && after != null) {
                    transaction = EditorEditTransaction.capture(before.text.toString(),
                            before.start, before.end, after.text.toString(), after.start, after.end);
                    if (transaction != null) {
                        if (recordInJournal(connection, before, after)) transaction = null;
                        else {
                            original = before;
                            pasted = after;
                            journalConnection = new WeakReference<>(connection);
                        }
                    }
                }
            }
            return accepted;
        } catch (Throwable ignored) {
            clearHistory();
            return false;
        } finally {
            dispatching = false;
        }
    }

    /** Any native card attempt invalidates older local history, including image/URI paths. */
    public void nativeClipboardAttempt() {
        clearHistory();
    }

    /** Called only immediately before the stock clipboard text-event dispatcher, never images. */
    public long beginNativePaste(String text) {
        observeNativePaste();
        clearHistory();
        if (dispatching || composing || text == null || text.isEmpty()
                || text.length() > EditorEditTransaction.MAX_CHARACTERS) return 0;
        Editor editor = active.get();
        long expected = generation;
        try {
            if (editor == null || !editor.allowSnapshots()) return 0;
            InputConnection connection = editor.connection();
            Snapshot before = connection == null ? null : snapshot(connection);
            if (before == null || !current(editor, connection, expected)) return 0;
            JournalingInputConnection wrapper = JournalingInputConnection.forConnection(connection);
            if (wrapper != null) wrapper.breakGroup();
            int low = Math.min(before.start, before.end), high = Math.max(before.start, before.end);
            if (before.text.length() - (high - low) + text.length()
                    > EditorEditTransaction.MAX_CHARACTERS) return 0;
            String after = before.text.subSequence(0, low).toString() + text
                    + before.text.subSequence(high, before.text.length());
            int cursor = low + text.length();
            EditorEditTransaction edit = EditorEditTransaction.capture(before.text.toString(),
                    before.start, before.end, after, cursor, cursor);
            if (edit == null) return 0;
            long token = ++pasteSequence;
            pendingPaste = new PendingPaste(token, expected, editor, connection, before, edit);
            return token;
        } catch (Throwable ignored) { clearHistory(); return 0; }
    }

    /** A callback is only a signal: the entire bounded replacement must also be observed. */
    public void observeNativePaste() {
        PendingPaste pending = pendingPaste;
        if (pending == null || dispatching) return;
        try {
            Editor editor = pending.editor.get();
            InputConnection connection = pending.connection.get();
            if (editor == null || connection == null || !editor.allowSnapshots()
                    || !current(editor, connection, pending.generation)) { clearHistory(); return; }
            Snapshot after = snapshot(connection);
            if (pendingPaste != pending || !current(editor, connection, pending.generation)) return;
            if (pending.before.same(after)) return; // Dispatch may still be queued.
            clearHistory();
            if (after != null && pending.edit.matches(after.text.toString(), after.start, after.end)) {
                JournalingInputConnection wrapper = JournalingInputConnection.forConnection(connection);
                if (wrapper != null) wrapper.breakGroup();
                if (!recordInJournal(connection, pending.before, after)) {
                    transaction = pending.edit;
                    original = pending.before;
                    pasted = after;
                    journalConnection = new WeakReference<>(connection);
                    // Native card dispatch may be asynchronous. Never issue a competing
                    // host undo before applying this exact verified local inverse.
                    localUndo = true;
                }
            }
        } catch (Throwable ignored) { clearHistory(); }
    }

    public void expireNativePaste(long token) {
        if (pendingPaste != null && pendingPaste.token == token) clearHistory();
    }

    private static final class PendingPaste {
        final long token, generation;
        final WeakReference<Editor> editor;
        final WeakReference<InputConnection> connection;
        final Snapshot before;
        final EditorEditTransaction edit;
        PendingPaste(long token, long generation, Editor editor, InputConnection connection,
                Snapshot before, EditorEditTransaction edit) {
            this.token = token; this.generation = generation;
            this.editor = new WeakReference<>(editor);
            this.connection = new WeakReference<>(connection);
            this.before = before; this.edit = edit;
        }
    }

    private boolean reverse(Editor editor, InputConnection connection, long expected, Snapshot before) {
        EditorEditTransaction edit = transaction;
        Snapshot target = edit.undone ? pasted : original;
        // Try the host first. Observe the result; true alone does not prove an edit occurred.
        if (!localUndo) {
            connection.performContextMenuAction(edit.undone ? android.R.id.redo : android.R.id.undo);
        }
        Snapshot observed = snapshot(connection);
        if (!current(editor, connection, expected) || observed == null) {
            clearHistory();
            return false;
        }
        if (target.same(observed)) {
            edit.undone = !edit.undone;
            return true;
        }
        if (!before.same(observed)) {
            // The host changed something else. Never apply another edit on top of that.
            clearHistory();
            return true;
        }
        // Only this exact known range may be replaced. Never rewrite the entire field.
        int from = edit.offset;
        int to = edit.undone ? Math.max(edit.selectionStart, edit.selectionEnd) : edit.insertedEnd;
        CharSequence replacement = edit.undone
                ? pasted.text.subSequence(from, edit.insertedEnd)
                : original.text.subSequence(from, Math.max(edit.selectionStart, edit.selectionEnd));
        pendingOwnSelections.clear();
        boolean batch = false;
        try {
            connection.beginBatchEdit();
            batch = true;
            if (!current(editor, connection, expected) || !setOwnSelection(connection, from, to)
                    || !current(editor, connection, expected)) {
                clearHistory();
                return false;
            }
            pendingOwnSelections.add(new int[] {from + replacement.length(), from + replacement.length()});
            if (!connection.commitText(replacement, 1)) {
                clearHistory();
                return false;
            }
            if (!current(editor, connection, expected)
                    || !setOwnSelection(connection, target.start, target.end)) {
                clearHistory();
                return false;
            }
        } finally {
            if (batch) connection.endBatchEdit();
        }
        Snapshot result = snapshot(connection);
        if (!current(editor, connection, expected) || result == null || !target.same(result)) {
            clearHistory();
            return false;
        }
        localUndo = true;
        edit.undone = !edit.undone;
        return true;
    }

    /** Null when the keyboard journal cannot act; the caller keeps the existing paths. */
    private Boolean journalHistory(Editor editor, InputConnection connection, long expected, boolean isUndo) {
        if (!editor.allowSnapshots()) return null;
        JournalingInputConnection wrapper = JournalingInputConnection.forConnection(connection);
        if (wrapper == null) return null;
        InputConnection raw = wrapper.raw;
        wrapper.seal();
        ImeEditJournal.State state = wrapper.state();
        ImeEditJournal.Edit edit = state == null ? null
                : isUndo ? wrapper.journal.peekUndo(state) : wrapper.journal.peekRedo(state);
        if (!current(editor, connection, expected)) return false;
        if (edit == null) {
            // An exhausted local chain must not fall through to an unrelated host undo.
            return wrapper.journal.owns(state) ? Boolean.FALSE : null;
        }
        // The journal owns this state now; an older paste transaction must not replay over it.
        clearHistory();
        ImeEditJournal.State target = ImeEditJournal.target(state, edit, isUndo);
        int from = edit.offset;
        int to = isUndo ? edit.insertedEnd() : edit.removedEnd();
        String replacement = isUndo ? edit.removed : edit.inserted;
        boolean batch = false;
        try {
            // Raw connection: reversing must not be recorded as a new keyboard edit.
            raw.beginBatchEdit();
            batch = true;
            raw.finishComposingText();
            if (!raw.setSelection(from, to) || !raw.commitText(replacement, 1)
                    || !raw.setSelection(target.start, target.end)) {
                wrapper.journal.clear();
                return false;
            }
        } finally {
            if (batch) raw.endBatchEdit();
        }
        ImeEditJournal.State observed = wrapper.state();
        return current(editor, connection, expected) && wrapper.journal.applied(edit, isUndo, target, observed);
    }

    private boolean setOwnSelection(InputConnection connection, int start, int end) {
        pendingOwnSelections.add(new int[] {start, end});
        return connection.setSelection(start, end);
    }

    /** The same bounded history owns typing and verified paste/cut, but never styled spans. */
    private static boolean recordInJournal(InputConnection connection, Snapshot before, Snapshot after) {
        if (before == null || after == null || !before.spans.isEmpty() || !after.spans.isEmpty())
            return false;
        JournalingInputConnection wrapper = JournalingInputConnection.forConnection(connection);
        return wrapper != null && wrapper.recordObserved(
                new ImeEditJournal.State(before.text.toString(), before.start, before.end),
                new ImeEditJournal.State(after.text.toString(), after.start, after.end));
    }

    private boolean current(Editor editor, InputConnection connection, long expected) {
        return generation == expected && active.get() == editor && editor.connection() == connection;
    }

    private static Snapshot snapshot(InputConnection connection) {
        try { return readSnapshot(connection); }
        catch (Throwable ignored) { return null; } // Optional extraction must not block native actions.
    }

    private static Snapshot readSnapshot(InputConnection connection) {
        ExtractedTextRequest request = new ExtractedTextRequest();
        request.hintMaxChars = EditorEditTransaction.MAX_CHARACTERS;
        request.flags = InputConnection.GET_TEXT_WITH_STYLES;
        ExtractedText value = connection.getExtractedText(request, 0);
        if (value == null || value.text == null || value.startOffset != 0
                || value.partialStartOffset != -1 || value.partialEndOffset != -1
                || value.text.length() > EditorEditTransaction.MAX_CHARACTERS
                || value.selectionStart < 0 || value.selectionEnd < 0
                || value.selectionStart > value.text.length() || value.selectionEnd > value.text.length()) return null;
        CharSequence frozen = value.text instanceof Spanned ? new SpannedString(value.text) : value.text.toString();
        if (frozen.length() > EditorEditTransaction.MAX_CHARACTERS
                || value.selectionStart > frozen.length() || value.selectionEnd > frozen.length()) return null;
        Map<List<Object>, Integer> spanValues = StableEditorSpans.describe(frozen);
        if (spanValues == null) return null;
        return new Snapshot(frozen, value.selectionStart, value.selectionEnd, spanValues);
    }

    private static final class Snapshot {
        final CharSequence text;
        final int start, end;
        final Map<List<Object>, Integer> spans;
        Snapshot(CharSequence text, int start, int end, Map<List<Object>, Integer> spans) {
            this.spans = spans;
            this.text = text;
            this.start = start;
            this.end = end;
        }
        boolean same(Snapshot other) {
            return other != null && start == other.start && end == other.end
                    && text.toString().contentEquals(other.text)
                    && spans.equals(other.spans);
        }
    }
}
