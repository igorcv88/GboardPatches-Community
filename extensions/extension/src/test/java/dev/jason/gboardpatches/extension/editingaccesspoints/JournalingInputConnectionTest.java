package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.view.inputmethod.InputConnection;
import android.view.inputmethod.ExtractedText;
import java.lang.reflect.Proxy;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

/** Keyboard edits through the wrapper become host-independent undo/redo steps. */
@RunWith(RobolectricTestRunner.class)
public class JournalingInputConnectionTest {
    /** Minimal editor whose host undo does nothing, like many non-EditText editors. */
    static final class FakeEditor {
        final StringBuilder text = new StringBuilder();
        String clipboard = "pasted 😃\n";
        int start, end, composeStart = -1, composeEnd = -1, hostUndo, mutations;
        final InputConnection connection = (InputConnection) Proxy.newProxyInstance(
                FakeEditor.class.getClassLoader(), new Class<?>[] {InputConnection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "commitText": mutations++; replace(String.valueOf(args[0]), false); return true;
                        case "setComposingText": mutations++; replace(String.valueOf(args[0]), true); return true;
                        case "finishComposingText": composeStart = composeEnd = -1; return true;
                        case "deleteSurroundingText": {
                            mutations++;
                            int before = (Integer) args[0], after = (Integer) args[1];
                            int a = Math.max(0, start - before), b = Math.min(text.length(), end + after);
                            text.delete(end, b); text.delete(a, start); start = end = a; return true;
                        }
                        case "setSelection": start = (Integer) args[0]; end = (Integer) args[1]; return true;
                        case "getExtractedText": {
                            ExtractedText extracted = new ExtractedText();
                            extracted.text = text.toString(); extracted.startOffset = 0;
                            extracted.partialStartOffset = extracted.partialEndOffset = -1;
                            extracted.selectionStart = start; extracted.selectionEnd = end;
                            return extracted;
                        }
                        case "performContextMenuAction": {
                            int id = (Integer) args[0];
                            if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
                                replace(clipboard, false);
                            } else if (id == android.R.id.cut) {
                                replace("", false);
                            } else hostUndo++;
                            return true;
                        }
                        case "beginBatchEdit": case "endBatchEdit": return true;
                        default:
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            return null;
                    }
                });
        void replace(String value, boolean composing) {
            int a = composeStart >= 0 ? composeStart : Math.min(start, end);
            int b = composeStart >= 0 ? composeEnd : Math.max(start, end);
            text.replace(a, b, value);
            start = end = a + value.length();
            if (composing) { composeStart = a; composeEnd = a + value.length(); }
            else composeStart = composeEnd = -1;
        }
        ImeEditJournal.State state() { return new ImeEditJournal.State(text.toString(), start, end); }
    }

    private FakeEditor setUp(EditorActionSession session) {
        FakeEditor editor = new FakeEditor();
        JournalingInputConnection.setReader(connection -> editor.state());
        JournalingInputConnection.setPolicy(() -> true);
        session.begin(new EditorActionSession.Editor() {
            @Override public InputConnection connection() { return editor.connection; }
            @Override public boolean allowSnapshots() { return true; }
        });
        return editor;
    }

    @After public void tearDown() {
        JournalingInputConnection.setReader(null);
        JournalingInputConnection.setPolicy(null);
        JournalingInputConnection.resetAll();
    }

    @Test public void wrapperIsStableTransparentAndUnwraps() {
        FakeEditor editor = new FakeEditor();
        InputConnection wrapped = JournalingInputConnection.wrap(editor.connection);
        assertSame(wrapped, JournalingInputConnection.wrap(editor.connection));
        assertSame(wrapped, JournalingInputConnection.wrap(wrapped));
        assertSame(editor.connection, JournalingInputConnection.unwrap(wrapped));
        assertNull(JournalingInputConnection.wrap(null));
        wrapped.commitText("abc", 1);
        assertEquals("abc", editor.text.toString());
    }

    @Test public void undoAndRedoComposedTypingWithoutHostUndo() {
        EditorActionSession session = new EditorActionSession();
        FakeEditor editor = setUp(session);
        InputConnection keyboard = JournalingInputConnection.wrap(editor.connection);
        keyboard.setComposingText("h", 1);
        keyboard.setComposingText("hi", 1);
        keyboard.commitText("hi ", 1);
        keyboard.beginBatchEdit();
        keyboard.setComposingText("there", 1);
        keyboard.endBatchEdit();
        keyboard.finishComposingText();
        assertEquals("hi there", editor.text.toString());

        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("hi ", editor.text.toString());
        assertEquals(3, editor.start);
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("", editor.text.toString());
        assertTrue(session.execute(EditorAction.REDO));
        assertEquals("hi ", editor.text.toString());
        assertEquals(0, editor.hostUndo);
    }

    @Test public void undoRestoresBackspacedText() {
        EditorActionSession session = new EditorActionSession();
        FakeEditor editor = setUp(session);
        editor.text.append("keep this"); editor.start = editor.end = 9;
        InputConnection keyboard = JournalingInputConnection.wrap(editor.connection);
        keyboard.deleteSurroundingText(1, 0);
        keyboard.deleteSurroundingText(1, 0);
        assertEquals("keep th", editor.text.toString());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("keep this", editor.text.toString());
        assertEquals(9, editor.start);
    }

    @Test public void externalEditFallsBackToHostAndClearsJournal() {
        EditorActionSession session = new EditorActionSession();
        FakeEditor editor = setUp(session);
        InputConnection keyboard = JournalingInputConnection.wrap(editor.connection);
        keyboard.commitText("ok", 1);
        editor.text.append("!"); editor.start = editor.end = 3; // App-side change.
        session.execute(EditorAction.UNDO);
        assertEquals("ok!", editor.text.toString());
        assertEquals(1, editor.hostUndo); // Delegated, never applied over the external change.
    }

    @Test public void deniedPolicyRecordsNothing() {
        EditorActionSession session = new EditorActionSession();
        FakeEditor editor = setUp(session);
        JournalingInputConnection.setPolicy(() -> false);
        InputConnection keyboard = JournalingInputConnection.wrap(editor.connection);
        keyboard.commitText("secret", 1);
        session.execute(EditorAction.UNDO);
        assertEquals("secret", editor.text.toString());
        assertEquals(1, editor.hostUndo);
    }

    @Test public void wrapperEqualsItsEditorConnection() {
        FakeEditor editor = new FakeEditor();
        InputConnection wrapped = JournalingInputConnection.wrap(editor.connection);
        // Loup.az(): Objects.equals(getCurrentInputConnection(), binding.getConnection()).
        assertTrue(java.util.Objects.equals(wrapped, editor.connection));
        assertEquals(editor.connection.hashCode(), wrapped.hashCode());
        assertFalse(wrapped.equals(new FakeEditor().connection));
    }

    /** Mirrors Loup.az() (0x2f01e4): returns !Objects.equals(current, binding.getConnection()). */
    private static boolean realEditorBound(InputConnection current, InputConnection binding) {
        return !java.util.Objects.equals(current, binding);
    }

    @Test public void loupAzKeepsDummyAndRealConnectionSemantics() {
        FakeEditor dummy = new FakeEditor(), editor = new FakeEditor();
        // No editor started: IMS returns the binding's own (dummy) connection.
        assertFalse(realEditorBound(JournalingInputConnection.wrap(dummy.connection), dummy.connection));
        // Editor started: current connection differs from the binding connection.
        assertTrue(realEditorBound(JournalingInputConnection.wrap(editor.connection), dummy.connection));
    }

    @Test public void composingRunCostsOneSnapshotPair() {
        EditorActionSession session = new EditorActionSession();
        FakeEditor editor = setUp(session);
        JournalingInputConnection.drainCounters();
        int[] reads = {0};
        JournalingInputConnection.setReader(connection -> { reads[0]++; return editor.state(); });
        InputConnection keyboard = JournalingInputConnection.wrap(editor.connection);
        for (String partial : new String[] {"p", "pa", "pal", "pala", "palav", "palavr", "palavra"}) {
            keyboard.beginBatchEdit();
            keyboard.setComposingText(partial, 1);
            keyboard.endBatchEdit();
        }
        assertEquals(1, reads[0]);
        keyboard.commitText("palavra ", 1);
        assertEquals(2, reads[0]);
        String counters = JournalingInputConnection.drainCounters();
        assertTrue(counters, counters.startsWith("commit=1 compose=7 delete=0"));
        assertTrue(counters, counters.endsWith("snapshots=2"));
        assertNull(JournalingInputConnection.drainCounters());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("", editor.text.toString());
    }

    @Test public void undoWhileComposingSealsTheRunFirst() {
        EditorActionSession session = new EditorActionSession();
        FakeEditor editor = setUp(session);
        InputConnection keyboard = JournalingInputConnection.wrap(editor.connection);
        keyboard.commitText("ok ", 1);
        keyboard.setComposingText("wor", 1);
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("ok ", editor.text.toString());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("", editor.text.toString());
    }

    @Test public void reversalIsNotRecordedAsKeyboardEdit() {
        EditorActionSession session = new EditorActionSession();
        FakeEditor editor = setUp(session);
        InputConnection keyboard = JournalingInputConnection.wrap(editor.connection);
        keyboard.commitText("word", 1);
        int before = editor.mutations;
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals(before + 1, editor.mutations);
        assertEquals(0, JournalingInputConnection.forConnection(editor.connection).journal.undoSize());
        assertEquals(1, JournalingInputConnection.forConnection(editor.connection).journal.redoSize());
    }

    @Test public void toolbarPasteAndTypingShareOrderedHistory() {
        EditorActionSession session = new EditorActionSession();
        FakeEditor editor = setUp(session);
        InputConnection keyboard = JournalingInputConnection.wrap(editor.connection);
        keyboard.commitText("hello ", 1);
        assertTrue(session.execute(EditorAction.PASTE));
        keyboard.commitText("end", 1);
        assertEquals("hello pasted 😃\nend", editor.text.toString());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("hello pasted 😃\n", editor.text.toString());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("hello ", editor.text.toString());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("", editor.text.toString());
        assertFalse(session.execute(EditorAction.UNDO));
        assertEquals(0, editor.hostUndo); // No unrelated native undo after exhaustion.
        assertTrue(session.execute(EditorAction.REDO));
        assertTrue(session.execute(EditorAction.REDO));
        assertTrue(session.execute(EditorAction.REDO));
        assertEquals("hello pasted 😃\nend", editor.text.toString());
        assertFalse(session.execute(EditorAction.REDO));
        assertEquals(0, editor.hostUndo);
    }

    @Test public void nativeClipboardCardAndTypingShareOrderedHistory() {
        EditorActionSession session = new EditorActionSession();
        FakeEditor editor = setUp(session);
        InputConnection keyboard = JournalingInputConnection.wrap(editor.connection);
        keyboard.commitText("before ", 1);
        long token = session.beginNativePaste(editor.clipboard);
        assertTrue(token > 0);
        editor.replace(editor.clipboard, false); // Gboard's stock event bus, not the wrapper.
        session.observeNativePaste();
        keyboard.commitText("after", 1);
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("before " + editor.clipboard, editor.text.toString());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("before ", editor.text.toString());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("", editor.text.toString());
        assertTrue(session.execute(EditorAction.REDO));
        assertTrue(session.execute(EditorAction.REDO));
        assertTrue(session.execute(EditorAction.REDO));
        assertEquals("before " + editor.clipboard + "after", editor.text.toString());
        assertEquals(0, editor.hostUndo);
    }

    @Test public void contextPastePlainTextAndCutAreIndependentSteps() {
        EditorActionSession session = new EditorActionSession();
        FakeEditor editor = setUp(session);
        InputConnection keyboard = JournalingInputConnection.wrap(editor.connection);
        keyboard.commitText("abc", 1);
        assertTrue(keyboard.performContextMenuAction(android.R.id.pasteAsPlainText));
        assertEquals("abc" + editor.clipboard, editor.text.toString());
        keyboard.setSelection(0, 3);
        assertTrue(keyboard.performContextMenuAction(android.R.id.cut));
        assertEquals(editor.clipboard, editor.text.toString());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("abc" + editor.clipboard, editor.text.toString());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("abc", editor.text.toString());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("", editor.text.toString());
        assertEquals(0, editor.hostUndo);
    }

    @Test public void directMultiCharacterCommitDoesNotMergeWithAdjacentTyping() {
        EditorActionSession session = new EditorActionSession();
        FakeEditor editor = setUp(session);
        InputConnection keyboard = JournalingInputConnection.wrap(editor.connection);
        keyboard.commitText("a", 1);
        keyboard.commitText("b", 1);
        keyboard.commitText("CLIP", 1); // Clipboard chip / suggestion via commitText.
        keyboard.commitText("c", 1);
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("abCLIP", editor.text.toString());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("ab", editor.text.toString());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("", editor.text.toString());
    }

    @Test public void preRecordedCardIsNotDuplicatedWhenVerifiedByEventHook() {
        EditorActionSession session = new EditorActionSession();
        FakeEditor editor = setUp(session);
        InputConnection keyboard = JournalingInputConnection.wrap(editor.connection);
        assertTrue(session.beginNativePaste(editor.clipboard) > 0);
        keyboard.commitText(editor.clipboard, 1); // Also intercepted by the wrapper.
        session.observeNativePaste();
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("", editor.text.toString());
        assertFalse(session.execute(EditorAction.UNDO));
        assertEquals(0, editor.hostUndo);
    }

    @Test public void oneCharacterNativeCardStaysSeparateFromTypedWord() {
        EditorActionSession session = new EditorActionSession();
        FakeEditor editor = setUp(session);
        InputConnection keyboard = JournalingInputConnection.wrap(editor.connection);
        keyboard.commitText("a", 1);
        assertTrue(session.beginNativePaste("X") > 0);
        keyboard.commitText("X", 1);
        session.observeNativePaste();
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("a", editor.text.toString());
        assertTrue(session.execute(EditorAction.UNDO));
        assertEquals("", editor.text.toString());
    }
}
