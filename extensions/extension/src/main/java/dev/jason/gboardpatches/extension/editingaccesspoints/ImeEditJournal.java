package dev.jason.gboardpatches.extension.editingaccesspoints;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bounded undo/redo history of edits the keyboard itself sent to one editor.
 *
 * <p>Edits are derived from full-text snapshots taken immediately before and after each
 * keyboard edit batch, never from the keyboard's intent. A snapshot that differs from the
 * last recorded state proves an external change (application, another IME path, user
 * gesture) and discards the whole history. Undo and redo are only offered while the editor
 * still shows exactly the recorded state. Pure Java: no Android types, never logged.
 */
public final class ImeEditJournal {
    public static final int MAX_CHARACTERS = EditorEditTransaction.MAX_CHARACTERS;
    static final int MAX_ENTRIES = 100;
    static final long GROUP_WINDOW_MS = 2000;

    /** Immutable editor state. Offsets are UTF-16 indices. */
    public static final class State {
        public final String text;
        public final int start, end;
        public State(String text, int start, int end) {
            this.text = text; this.start = start; this.end = end;
        }
        boolean same(State other) {
            return other != null && start == other.start && end == other.end && text.equals(other.text);
        }
    }

    /** One replacement: {@code removed} at {@code offset} became {@code inserted}. */
    public static final class Edit {
        public final int offset;
        public final String removed, inserted;
        public final int beforeStart, beforeEnd, afterStart, afterEnd;
        final long time;
        Edit(int offset, String removed, String inserted,
                int beforeStart, int beforeEnd, int afterStart, int afterEnd, long time) {
            this.offset = offset; this.removed = removed; this.inserted = inserted;
            this.beforeStart = beforeStart; this.beforeEnd = beforeEnd;
            this.afterStart = afterStart; this.afterEnd = afterEnd; this.time = time;
        }
        int insertedEnd() { return offset + inserted.length(); }
        int removedEnd() { return offset + removed.length(); }
    }

    private final Deque<Edit> undo = new ArrayDeque<>();
    private final Deque<Edit> redo = new ArrayDeque<>();
    private State recorded;
    private State pendingBase;
    private boolean groupBreak;

    /** Called before a keyboard edit batch. {@code current} may be null (unreadable editor). */
    public void beforeEdit(State current) {
        if (pendingBase != null) return; // Nested batch: the outer base stays authoritative.
        if (current == null) { clear(); return; }
        if (recorded != null && !recorded.text.equals(current.text)) clear();
        else if (recorded != null && !recorded.same(current)) breakGroup();
        pendingBase = current;
    }

    /** Called once the keyboard edit batch has been delivered. */
    public void afterEdit(State current, long now) {
        State base = pendingBase;
        pendingBase = null;
        if (base == null) return;
        if (current == null) { clear(); return; }
        Edit edit = diff(base, current, now);
        recorded = current;
        if (edit == null) return; // Selection/composing-only change.
        redo.clear();
        Edit top = undo.peekLast();
        Edit merged = top == null || groupBreak ? null : merge(top, edit);
        groupBreak = false;
        if (merged != null) {
            undo.pollLast();
            if (merged.removed.equals(merged.inserted)) return; // Typed then fully erased.
            edit = merged;
        }
        undo.addLast(edit);
        while (undo.size() > MAX_ENTRIES) undo.pollFirst();
    }

    /** Next keyboard edit starts a new undo step (for example after an undo/redo or a cursor move). */
    public void breakGroup() { groupBreak = true; }

    // Moving the caret is not an edit. Verify the complete text before touching a known range;
    // undo restores that step's own selection, regardless of the current caret position.
    public boolean canUndo(State current) { return !undo.isEmpty() && matchesText(current); }
    public boolean canRedo(State current) { return !redo.isEmpty() && matchesText(current); }

    private boolean matchesText(State current) {
        return current != null && recorded != null && recorded.text.equals(current.text);
    }

    /** Whether this editor state belongs to a still-live local undo/redo chain. */
    public boolean owns(State current) {
        return matchesText(current) && (!undo.isEmpty() || !redo.isEmpty());
    }

    /**
     * Include a separately verified paste/cut in the same history as keyboard typing.
     * The operation is always an independent step, even when it immediately follows a word.
     * A paste which the wrapper already recorded must never appear twice.
     */
    public boolean recordObserved(State before, State after, long now) {
        if (before == null || after == null || before.text.equals(after.text)
                || pendingBase != null) return false;
        Edit last = undo.peekLast();
        if (recorded != null && recorded.same(after) && last != null
                && target(after, last, true).same(before)) return true;
        breakGroup();
        beforeEdit(before);
        afterEdit(after, now);
        breakGroup();
        return canUndo(after);
    }

    /** Edit to reverse, or null. The caller must apply it and then call {@link #applied}. */
    public Edit peekUndo(State current) { return canUndo(current) ? undo.peekLast() : null; }
    public Edit peekRedo(State current) { return canRedo(current) ? redo.peekLast() : null; }

    /** Expected state after reversing {@code edit} (undo) or reapplying it (redo). */
    public static State target(State current, Edit edit, boolean isUndo) {
        String text = isUndo
                ? current.text.substring(0, edit.offset) + edit.removed + current.text.substring(edit.insertedEnd())
                : current.text.substring(0, edit.offset) + edit.inserted + current.text.substring(edit.removedEnd());
        return isUndo ? new State(text, edit.beforeStart, edit.beforeEnd)
                : new State(text, edit.afterStart, edit.afterEnd);
    }

    /** Records an applied undo/redo. A mismatching result invalidates every step. */
    public boolean applied(Edit edit, boolean isUndo, State expected, State observed) {
        if (observed == null || !expected.same(observed)
                || (isUndo ? undo.peekLast() : redo.peekLast()) != edit) {
            clear();
            return false;
        }
        if (isUndo) redo.addLast(undo.pollLast()); else undo.addLast(redo.pollLast());
        recorded = observed;
        groupBreak = true;
        return true;
    }

    public void clear() {
        undo.clear(); redo.clear();
        recorded = null; pendingBase = null; groupBreak = false;
    }

    int undoSize() { return undo.size(); }
    int redoSize() { return redo.size(); }

    static Edit diff(State before, State after, long now) {
        String a = before.text, b = after.text;
        if (a.equals(b)) return null;
        int prefix = 0, max = Math.min(a.length(), b.length());
        while (prefix < max && a.charAt(prefix) == b.charAt(prefix)) prefix++;
        int suffix = 0;
        while (suffix < max - prefix
                && a.charAt(a.length() - 1 - suffix) == b.charAt(b.length() - 1 - suffix)) suffix++;
        // Never split a surrogate pair.
        if (prefix > 0 && Character.isHighSurrogate(a.charAt(prefix - 1))) prefix--;
        while (suffix > 0 && Character.isLowSurrogate(a.charAt(a.length() - suffix))) suffix--;
        return new Edit(prefix, a.substring(prefix, a.length() - suffix), b.substring(prefix, b.length() - suffix),
                before.start, before.end, after.start, after.end, now);
    }

    /**
     * Groups word typing, composing/autocorrect rewrites and consecutive backspaces.
     * A typed whitespace or newline closes a word: the next word becomes a new step.
     */
    static Edit merge(Edit top, Edit next) {
        if (next.time - top.time > GROUP_WINDOW_MS) return null;
        String inserted = top.inserted;
        // Rewrite or extension inside the region this step inserted (composing, autocorrect,
        // erasing characters that were just typed).
        if (next.offset >= top.offset && next.removedEnd() <= top.insertedEnd()) {
            boolean appending = next.removed.isEmpty() && next.offset == top.insertedEnd();
            if (appending && endsWord(inserted) && !next.inserted.isEmpty() && !isBlank(next.inserted)) return null;
            int at = next.offset - top.offset;
            if (!inserted.regionMatches(at, next.removed, 0, next.removed.length())) return null;
            String rewritten = inserted.substring(0, at) + next.inserted + inserted.substring(at + next.removed.length());
            return new Edit(top.offset, top.removed, rewritten,
                    top.beforeStart, top.beforeEnd, next.afterStart, next.afterEnd, next.time);
        }
        // Consecutive backspaces before a pure deletion step.
        if (inserted.isEmpty() && next.inserted.isEmpty() && next.removedEnd() == top.offset) {
            return new Edit(next.offset, next.removed + top.removed, "",
                    top.beforeStart, top.beforeEnd, next.afterStart, next.afterEnd, next.time);
        }
        return null;
    }

    private static boolean endsWord(String value) {
        return !value.isEmpty() && Character.isWhitespace(value.charAt(value.length() - 1));
    }

    private static boolean isBlank(String value) {
        for (int i = 0; i < value.length(); i++) if (!Character.isWhitespace(value.charAt(i))) return false;
        return true;
    }
}
