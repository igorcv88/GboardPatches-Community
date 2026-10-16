package dev.jason.gboardpatches.extension.clipboard;

/**
 * Lifecycle of the composite clipboard search: results surface above the IME's own
 * alphabet keyboard, with keyboard input routed only to the search field.
 *
 * <p>Ordering is the isolation guarantee. Opening routes input last, after the surface and
 * the alphabet keyboard are in place; every exit restores the editor's input provider first,
 * so no keystroke can reach a detached field and no query text can reach the editor.
 * Any failed step rolls back the steps already taken. Pure Java; all IME work is in {@link Host}.
 */
public final class ClipboardNativeSearchSession {
    public interface Host {
        /** Shows the results surface above the keyboard. */
        boolean showSurface();
        void hideSurface();
        /** Replaces the clipboard body with the native alphabet keyboard. */
        boolean switchToAlphabet();
        /** Makes the search field the keyboard's input provider. */
        boolean routeToField();
        /** Makes the editor's own connection the input provider again. */
        void restoreRouting();
        /** Brings the native clipboard panel back (cancel path only). */
        void returnToClipboard();
        /** Commits a picked clip into the editor through the restored provider. */
        boolean commitToEditor(String text);
    }

    public enum State { CLOSED, OPEN }

    private final Host host;
    private State state = State.CLOSED;
    private boolean surface, alphabet, routed;

    public ClipboardNativeSearchSession(Host host) { this.host = host; }

    public State state() { return state; }

    public boolean open() {
        if (state == State.OPEN) return true;
        try {
            surface = host.showSurface();
            if (!surface) return rollback();
            alphabet = host.switchToAlphabet();
            if (!alphabet) return rollback();
            routed = host.routeToField();
            if (!routed) return rollback();
        } catch (Throwable failure) {
            return rollback();
        }
        state = State.OPEN;
        return true;
    }

    /** Cancel (close button, Back): leave search and show the native clipboard again. */
    public void cancel() { close(true); }

    /** Input session ended or the window hid: restore without switching keyboards. */
    public void end() { close(false); }

    /** Restores the editor connection first, then commits exactly the picked clip. */
    public boolean pick(String text) {
        if (state != State.OPEN || text == null) return false;
        close(false);
        try { return host.commitToEditor(text); }
        catch (Throwable ignored) { return false; }
    }

    private void close(boolean backToClipboard) {
        if (state != State.OPEN) return;
        state = State.CLOSED;
        boolean switched = alphabet;
        undo();
        if (backToClipboard && switched) {
            try { host.returnToClipboard(); } catch (Throwable ignored) { }
        }
    }

    private boolean rollback() {
        boolean switched = alphabet;
        undo();
        if (switched) {
            try { host.returnToClipboard(); } catch (Throwable ignored) { }
        }
        state = State.CLOSED;
        return false;
    }

    private void undo() {
        if (routed) {
            routed = false;
            try { host.restoreRouting(); } catch (Throwable ignored) { }
        }
        if (surface) {
            surface = false;
            try { host.hideSurface(); } catch (Throwable ignored) { }
        }
        alphabet = false;
    }
}
