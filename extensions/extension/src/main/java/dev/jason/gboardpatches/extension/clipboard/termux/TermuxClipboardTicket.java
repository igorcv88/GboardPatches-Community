package dev.jason.gboardpatches.extension.clipboard;

/** A stale view, repeat tap, or delayed confirmation cannot issue another insertion. */
final class TermuxClipboardTicket {
    private long revision;
    private Object source;
    private boolean claimed;

    long offer(Object value) {
        if (source != value) { source = value; claimed = false; revision++; }
        return revision;
    }
    boolean active(long expected) { return source != null && revision == expected && !claimed; }
    boolean claim(long expected) {
        if (!active(expected)) return false;
        claimed = true;
        return true;
    }
    void invalidate() { source = null; claimed = true; revision++; }
}
