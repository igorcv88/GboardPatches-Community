package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.text.Spanned;
import android.text.SpannedString;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import java.lang.ref.WeakReference;

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

    public void begin(Editor editor) {
        end();
        active = new WeakReference<>(editor);
    }

    public void end() {
        generation++;
        active.clear();
        clearHistory();
    }

    private void clearHistory() {
        transaction = null;
        localUndo = false;
        original = pasted = null;
        journalConnection.clear();
    }

    public void onSelectionUpdated(int start, int end, int candidateStart, int candidateEnd) {
        if (transaction == null || dispatching) return;
        Snapshot expected = transaction.undone ? original : pasted;
        if (candidateStart >= 0 || candidateEnd >= 0 || expected.start != start || expected.end != end) {
            clearHistory();
        }
    }

    public boolean execute(EditorAction action) {
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
            Snapshot before = editor.allowSnapshots() ? snapshot(connection) : null;
            if (!current(editor, connection, expected)) return false;
            if (transaction != null && (journalConnection.get() != connection || before == null
                    || !transaction.matches(before.text.toString(), before.start, before.end)
                    || !(transaction.undone ? original : pasted).same(before))) {
                clearHistory();
            }
            boolean historyAction = action == EditorAction.UNDO || action == EditorAction.REDO;
            if (historyAction) {
                // Once this journal has undone a replacement, only redo that exact transaction.
                if (transaction != null) {
                    if ((action == EditorAction.UNDO) == transaction.undone) return false;
                    return reverse(editor, connection, expected, before);
                }
                // Host-owned typing and external pastes stay with the host undo manager.
                boolean accepted = connection.performContextMenuAction(action.contextMenuId);
                Snapshot after = before == null ? null : snapshot(connection);
                return current(editor, connection, expected) && (before == null ? accepted
                        : after != null && !before.same(after));
            }
            clearHistory();
            boolean accepted = connection.performContextMenuAction(action.contextMenuId);
            if (before != null && (action == EditorAction.PASTE || action == EditorAction.CUT)) {
                Snapshot after = snapshot(connection);
                if (current(editor, connection, expected) && after != null) {
                    transaction = EditorEditTransaction.capture(before.text.toString(),
                            before.start, before.end, after.text.toString(), after.start, after.end);
                    if (transaction != null) {
                        original = before;
                        pasted = after;
                        journalConnection = new WeakReference<>(connection);
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
        boolean batch = false;
        try {
            connection.beginBatchEdit();
            batch = true;
            if (!current(editor, connection, expected) || !connection.setSelection(from, to)
                    || !current(editor, connection, expected)) {
                clearHistory();
                return false;
            }
            if (!connection.commitText(replacement, 1)) {
                clearHistory();
                return false;
            }
            if (!current(editor, connection, expected)
                    || !connection.setSelection(target.start, target.end)) {
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

    private boolean current(Editor editor, InputConnection connection, long expected) {
        return generation == expected && active.get() == editor && editor.connection() == connection;
    }

    private static Snapshot snapshot(InputConnection connection) {
        ExtractedTextRequest request = new ExtractedTextRequest();
        request.hintMaxChars = EditorEditTransaction.MAX_CHARACTERS;
        request.flags = InputConnection.GET_TEXT_WITH_STYLES;
        ExtractedText value = connection.getExtractedText(request, 0);
        if (value == null || value.text == null || value.startOffset != 0
                || value.partialStartOffset != -1 || value.partialEndOffset != -1
                || value.text.length() > EditorEditTransaction.MAX_CHARACTERS
                || value.selectionStart < 0 || value.selectionEnd < 0
                || value.selectionStart > value.text.length() || value.selectionEnd > value.text.length()) return null;
        if (value.text instanceof Spanned spans) {
            for (Object span : spans.getSpans(0, spans.length(), Object.class)) {
                if ((spans.getSpanFlags(span) & Spanned.SPAN_COMPOSING) != 0) return null;
            }
        }
        return new Snapshot(value.text instanceof Spanned ? new SpannedString(value.text)
                : value.text.toString(), value.selectionStart, value.selectionEnd);
    }

    private static final class Snapshot {
        final CharSequence text;
        final int start, end;
        Snapshot(CharSequence text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
        boolean same(Snapshot other) {
            return other != null && start == other.start && end == other.end
                    && text.toString().contentEquals(other.text)
                    && (!(text instanceof Spanned) && !(other.text instanceof Spanned)
                        || SpannedString.valueOf(text).equals(SpannedString.valueOf(other.text)));
        }
    }
}
