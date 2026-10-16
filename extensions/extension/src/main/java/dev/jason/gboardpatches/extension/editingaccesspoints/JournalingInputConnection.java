package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.os.SystemClock;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/**
 * Transparent wrapper around the connection Gboard obtains from its InputMethodService.
 * Every call is forwarded unchanged; text-changing calls are additionally bracketed by
 * bounded snapshots so {@link ImeEditJournal} can reconstruct what the keyboard changed.
 * Recording is skipped entirely while the policy denies it (feature off, password or
 * no-learning fields), so those editors see exactly one extra virtual call per operation.
 */
public final class JournalingInputConnection extends InputConnectionWrapper {
    /** Reads the editor state; null when it cannot be proven complete. */
    public interface Reader { ImeEditJournal.State read(InputConnection connection); }

    private static final Map<InputConnection, JournalingInputConnection> WRAPPERS = new WeakHashMap<>();
    private static volatile BooleanSupplier policy = () -> false;
    private static volatile Reader reader = JournalingInputConnection::readState;

    final InputConnection raw;
    final ImeEditJournal journal = new ImeEditJournal();
    private int batchDepth;
    private boolean recording;

    private JournalingInputConnection(InputConnection raw) {
        super(raw, false);
        this.raw = raw;
    }

    /** Patched InputMethodService.getCurrentInputConnection() result filter. */
    public static InputConnection wrap(InputConnection connection) {
        if (connection == null || connection instanceof JournalingInputConnection) return connection;
        try {
            synchronized (WRAPPERS) {
                // Stable identity: Gboard compares connections between calls.
                JournalingInputConnection wrapper = WRAPPERS.get(connection);
                if (wrapper == null) WRAPPERS.put(connection, wrapper = new JournalingInputConnection(connection));
                return wrapper;
            }
        } catch (Throwable ignored) {
            return connection;
        }
    }

    public static InputConnection unwrap(InputConnection connection) {
        return connection instanceof JournalingInputConnection wrapper ? wrapper.raw : connection;
    }

    /** Journal recorded for this exact editor connection (raw or wrapped), or null. */
    static JournalingInputConnection forConnection(InputConnection connection) {
        if (connection instanceof JournalingInputConnection wrapper) return wrapper;
        synchronized (WRAPPERS) { return connection == null ? null : WRAPPERS.get(connection); }
    }

    public static void setPolicy(BooleanSupplier value) { policy = value == null ? () -> false : value; }
    static void setReader(Reader value) { reader = value == null ? JournalingInputConnection::readState : value; }

    /** New input session: no history may cross editors or sessions. */
    public static void resetAll() {
        synchronized (WRAPPERS) {
            for (JournalingInputConnection wrapper : WRAPPERS.values()) wrapper.reset();
        }
    }

    synchronized void reset() { journal.clear(); batchDepth = 0; recording = false; }

    synchronized ImeEditJournal.State state() { return reader.read(raw); }

    // ---- recording ----

    private synchronized void before() {
        if (batchDepth > 0 && recording) return;
        boolean allowed;
        try { allowed = policy.getAsBoolean(); } catch (Throwable ignored) { allowed = false; }
        if (!allowed) { journal.clear(); recording = false; return; }
        recording = true;
        journal.beforeEdit(reader.read(raw));
    }

    private synchronized void after() {
        if (!recording || batchDepth > 0) return;
        recording = false;
        journal.afterEdit(reader.read(raw), SystemClock.uptimeMillis());
    }

    @Override public boolean beginBatchEdit() {
        synchronized (this) { batchDepth++; }
        return super.beginBatchEdit();
    }

    @Override public boolean endBatchEdit() {
        boolean result = super.endBatchEdit();
        synchronized (this) {
            if (batchDepth > 0) batchDepth--;
            after();
        }
        return result;
    }

    @Override public boolean commitText(CharSequence text, int newCursorPosition) {
        before();
        try { return super.commitText(text, newCursorPosition); } finally { after(); }
    }

    @Override public boolean setComposingText(CharSequence text, int newCursorPosition) {
        before();
        try { return super.setComposingText(text, newCursorPosition); } finally { after(); }
    }

    @Override public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        before();
        try { return super.deleteSurroundingText(beforeLength, afterLength); } finally { after(); }
    }

    @Override public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        before();
        try { return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength); } finally { after(); }
    }

    @Override public boolean commitCompletion(CompletionInfo text) {
        before();
        try { return super.commitCompletion(text); } finally { after(); }
    }

    @Override public boolean commitCorrection(CorrectionInfo info) {
        before();
        try { return super.commitCorrection(info); } finally { after(); }
    }

    @Override public boolean sendKeyEvent(KeyEvent event) {
        // DEL/FORWARD_DEL/ENTER key events edit text on ACTION_DOWN in apps that ignore
        // deleteSurroundingText. Bracket the down event; the up event never edits.
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return super.sendKeyEvent(event);
        before();
        try { return super.sendKeyEvent(event); } finally { after(); }
    }

    @Override public boolean setSelection(int start, int end) {
        synchronized (this) { journal.breakGroup(); }
        return super.setSelection(start, end);
    }

    @Override public boolean performContextMenuAction(int id) {
        // Host paste/cut/undo change text outside this journal; the next snapshot proves it.
        synchronized (this) { journal.breakGroup(); }
        return super.performContextMenuAction(id);
    }

    // ---- snapshots ----

    static ImeEditJournal.State readState(InputConnection connection) {
        try {
            ExtractedTextRequest request = new ExtractedTextRequest();
            request.hintMaxChars = ImeEditJournal.MAX_CHARACTERS;
            ExtractedText value = connection.getExtractedText(request, 0);
            if (value == null || value.text == null || value.startOffset != 0
                    || value.partialStartOffset != -1 || value.partialEndOffset != -1
                    || value.text.length() > ImeEditJournal.MAX_CHARACTERS
                    || value.selectionStart < 0 || value.selectionEnd < 0
                    || value.selectionStart > value.text.length()
                    || value.selectionEnd > value.text.length()) return null;
            String text = value.text instanceof Spanned ? value.text.toString() : String.valueOf(value.text);
            return new ImeEditJournal.State(text, value.selectionStart, value.selectionEnd);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
