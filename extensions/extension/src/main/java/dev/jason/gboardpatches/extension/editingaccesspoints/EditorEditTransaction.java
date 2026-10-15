package dev.jason.gboardpatches.extension.editingaccesspoints;

/** A single proven replacement, in UTF-16 offsets. No persistent editor history. */
final class EditorEditTransaction {
    static final int MAX_CHARACTERS = 32768;
    final String before, after;
    final int selectionStart, selectionEnd, offset, insertedEnd;
    boolean undone;

    private EditorEditTransaction(String before, String after, int start, int end, int insertedEnd) {
        this.before = before;
        this.after = after;
        this.selectionStart = start;
        this.selectionEnd = end;
        this.offset = Math.min(start, end);
        this.insertedEnd = insertedEnd;
    }

    static EditorEditTransaction capture(String before, int start, int end,
            String after, int afterStart, int afterEnd) {
        if (before == null || after == null || before.equals(after)
                || before.length() > MAX_CHARACTERS || after.length() > MAX_CHARACTERS) return null;
        int low = Math.min(start, end), high = Math.max(start, end);
        if (low < 0 || high > before.length() || !boundary(before, low)
                || !boundary(before, high)) return null;
        int insertedEnd = after.length() - (before.length() - high);
        if (insertedEnd < low || insertedEnd > after.length()
                || afterStart != insertedEnd || afterEnd != insertedEnd
                || !boundary(after, low) || !boundary(after, insertedEnd)
                || !after.regionMatches(0, before, 0, low)
                || !after.regionMatches(insertedEnd, before, high, before.length() - high)) return null;
        return new EditorEditTransaction(before, after, start, end, insertedEnd);
    }

    boolean matches(String text, int start, int end) {
        return undone
                ? before.equals(text) && start == selectionStart && end == selectionEnd
                : after.equals(text) && start == insertedEnd && end == insertedEnd;
    }

    private static boolean boundary(String text, int offset) {
        return offset == 0 || offset == text.length()
                || !Character.isHighSurrogate(text.charAt(offset - 1))
                || !Character.isLowSurrogate(text.charAt(offset));
    }
}
