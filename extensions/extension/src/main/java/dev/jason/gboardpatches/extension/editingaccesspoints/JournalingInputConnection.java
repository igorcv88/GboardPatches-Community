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
    /** Device validation counters: call kinds and snapshot reads only, never text. */
    static final java.util.concurrent.atomic.AtomicIntegerArray COUNTS = new java.util.concurrent.atomic.AtomicIntegerArray(6);
    static final int COMMIT = 0, COMPOSE = 1, DELETE = 2, KEY = 3, READ = 4, CORRECTION = 5;

    final InputConnection raw;
    final ImeEditJournal journal = new ImeEditJournal();
    private int batchDepth;
    private boolean recording;
    /** An open composing run: one snapshot pair per run, not per composing keystroke. */
    private boolean composing;

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

    synchronized void reset() { journal.clear(); batchDepth = 0; recording = false; composing = false; }

    /** Closes an open composing run so the journal reflects the editor before Undo/Redo. */
    synchronized void seal() {
        if (!composing) return;
        composing = false;
        after();
    }

    /**
     * Loup.az() compares getCurrentInputConnection() with InputBinding.getConnection() through
     * Objects.equals. The wrapper is the same editor connection, so it must compare equal to it.
     */
    @Override public boolean equals(Object other) { return other == this || other == raw; }
    @Override public int hashCode() { return raw.hashCode(); }

    synchronized ImeEditJournal.State state() { return read(); }

    synchronized void breakGroup() { journal.breakGroup(); }

    /** Records an independently verified native/toolbar replacement without losing typing. */
    synchronized boolean recordObserved(ImeEditJournal.State before, ImeEditJournal.State after) {
        boolean allowed;
        try { allowed = policy.getAsBoolean(); } catch (Throwable ignored) { allowed = false; }
        if (!allowed || batchDepth != 0 || composing || recording) return false;
        return journal.recordObserved(before, after, SystemClock.uptimeMillis());
    }

    private ImeEditJournal.State read() {
        COUNTS.incrementAndGet(READ);
        return reader.read(raw);
    }

    /** One line per input session when the wrapper saw keyboard edits; resets the counters. */
    public static String drainCounters() {
        int commits = COUNTS.getAndSet(COMMIT, 0), composes = COUNTS.getAndSet(COMPOSE, 0),
                deletes = COUNTS.getAndSet(DELETE, 0), keys = COUNTS.getAndSet(KEY, 0),
                reads = COUNTS.getAndSet(READ, 0), corrections = COUNTS.getAndSet(CORRECTION, 0);
        if (commits + composes + deletes + keys + corrections == 0) return null;
        return "commit=" + commits + " compose=" + composes + " delete=" + deletes
                + " keyDown=" + keys + " correction=" + corrections + " snapshots=" + reads;
    }

    // ---- recording ----

    private synchronized void before() {
        if (recording && (batchDepth > 0 || composing)) return; // Base already captured.
        boolean allowed;
        try { allowed = policy.getAsBoolean(); } catch (Throwable ignored) { allowed = false; }
        if (!allowed) { journal.clear(); recording = false; return; }
        recording = true;
        journal.beforeEdit(read());
    }

    private synchronized void after() {
        if (!recording || batchDepth > 0 || composing) return;
        recording = false;
        journal.afterEdit(read(), SystemClock.uptimeMillis());
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

    /** Before a mutation that ends any composing run; the run's base snapshot stays the base. */
    private synchronized void beforeFinal() {
        before();
        composing = false;
    }

    @Override public boolean commitText(CharSequence text, int newCursorPosition) {
        COUNTS.incrementAndGet(COMMIT);
        // Clipboard suggestions, voice input and other multi-character commits can arrive
        // directly as commitText, without a clipboard-card callback. Keep their insertion
        // separate from the word typed immediately before or after them.
        boolean atomic;
        synchronized (this) {
            atomic = !composing && text != null && text.length() > 1;
            if (atomic) journal.breakGroup();
        }
        beforeFinal();
        try { return super.commitText(text, newCursorPosition); }
        finally {
            after();
            if (atomic) synchronized (this) { journal.breakGroup(); }
        }
    }

    @Override public boolean setComposingText(CharSequence text, int newCursorPosition) {
        COUNTS.incrementAndGet(COMPOSE);
        synchronized (this) {
            // Intermediate composing updates of one word cost no IPC: the run is diffed once,
            // when it is committed, finished, or interrupted. External edits while a word is
            // being composed are attributed to the run (documented limitation).
            if (!composing) {
                before();
                composing = recording;
            }
        }
        return super.setComposingText(text, newCursorPosition);
    }

    @Override public boolean finishComposingText() {
        boolean result = super.finishComposingText();
        seal();
        return result;
    }

    @Override public boolean setComposingRegion(int start, int end) {
        seal();
        return super.setComposingRegion(start, end);
    }

    @Override public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        COUNTS.incrementAndGet(DELETE);
        beforeFinal();
        try { return super.deleteSurroundingText(beforeLength, afterLength); } finally { after(); }
    }

    @Override public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        COUNTS.incrementAndGet(DELETE);
        beforeFinal();
        try { return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength); } finally { after(); }
    }

    @Override public boolean commitCompletion(CompletionInfo text) {
        beforeFinal();
        try { return super.commitCompletion(text); } finally { after(); }
    }

    @Override public boolean commitCorrection(CorrectionInfo info) {
        COUNTS.incrementAndGet(CORRECTION);
        beforeFinal();
        try { return super.commitCorrection(info); } finally { after(); }
    }

    @Override public boolean sendKeyEvent(KeyEvent event) {
        // DEL/FORWARD_DEL/ENTER key events edit text on ACTION_DOWN in apps that ignore
        // deleteSurroundingText. Bracket the down event; the up event never edits.
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return super.sendKeyEvent(event);
        COUNTS.incrementAndGet(KEY);
        boolean clipboardShortcut = (event.isCtrlPressed()
                && (event.getKeyCode() == KeyEvent.KEYCODE_V || event.getKeyCode() == KeyEvent.KEYCODE_X))
                || (event.isShiftPressed() && event.getKeyCode() == KeyEvent.KEYCODE_INSERT);
        if (clipboardShortcut) synchronized (this) { journal.breakGroup(); }
        beforeFinal();
        try { return super.sendKeyEvent(event); }
        finally {
            after();
            if (clipboardShortcut) synchronized (this) { journal.breakGroup(); }
        }
    }

    @Override public boolean setSelection(int start, int end) {
        synchronized (this) { seal(); journal.breakGroup(); }
        return super.setSelection(start, end);
    }

    @Override public boolean performContextMenuAction(int id) {
        synchronized (this) { seal(); journal.breakGroup(); }
        // Native context-menu paste/cut may go through the wrapped connection without
        // EditorActionSession (clipboard suggestion, contextual selection menu, etc.).
        // Only a synchronous, observed replacement becomes one independent undo step.
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText
                || id == android.R.id.cut) {
            before();
            try { return super.performContextMenuAction(id); }
            finally {
                after();
                synchronized (this) { journal.breakGroup(); }
            }
        }
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
