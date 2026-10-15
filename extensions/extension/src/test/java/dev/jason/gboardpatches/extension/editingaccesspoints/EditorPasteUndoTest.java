package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.InputConnection;
import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;

public class EditorPasteUndoTest {
    static final class Host implements EditorActionSession.Editor {
        String text = "before after", clip = "😃\n colado";
        int start = 7, end = 7, commits, nativeCalls;
        boolean privateField, partial, nativeNoOp = true;
        Runnable onRead;
        final InputConnection connection = (InputConnection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {InputConnection.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "getExtractedText": {
                    if (onRead != null) onRead.run();
                    ExtractedText value = new ExtractedText();
                    value.text = text; value.startOffset = 0;
                    value.selectionStart = start; value.selectionEnd = end;
                    value.partialStartOffset = partial ? 0 : -1;
                    value.partialEndOffset = -1;
                    return value;
                }
                case "getSelectedText": return text.substring(Math.min(start, end), Math.max(start, end));
                case "performContextMenuAction": {
                    nativeCalls++;
                    int id = (Integer) a[0];
                    if (id == android.R.id.paste) replace(clip);
                    else if (id == android.R.id.cut) replace("");
                    else if (!nativeNoOp) replace("unexpected");
                    return true; // Acknowledged no-op for undo/redo reproduces the report.
                }
                case "setSelection": start = (Integer) a[0]; end = (Integer) a[1]; return true;
                case "commitText": commits++; replace(a[0].toString()); return true;
            }
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == int.class) return 0;
            return null;
        });
        void replace(String value) {
            int low = Math.min(start, end), high = Math.max(start, end);
            text = text.substring(0, low) + value + text.substring(high);
            start = end = low + value.length();
        }
        public InputConnection connection() { return connection; }
        public boolean allowSnapshots() { return !privateField; }
    }
    @Test public void toolbarPasteUndoRedoOnlyReplacesKnownRange() {
        Host h = new Host(); EditorActionSession s = new EditorActionSession(); s.begin(h);
        String original = h.text;
        assertTrue(s.execute(EditorAction.PASTE)); String pasted = h.text;
        assertTrue(s.execute(EditorAction.UNDO)); assertEquals(original, h.text);
        int nativeCalls = h.nativeCalls;
        assertTrue(s.execute(EditorAction.REDO)); assertEquals(pasted, h.text);
        assertEquals(nativeCalls, h.nativeCalls); // Local redo never touches stale native history.
        assertEquals(2, h.commits);
    }
    @Test public void cutAndSelectionReplacementRestoreOriginalSelection() {
        for (EditorAction action : new EditorAction[] {EditorAction.CUT, EditorAction.PASTE}) {
            Host h = new Host(); h.start = 10; h.end = 7;
            String original = h.text;
            EditorActionSession s = new EditorActionSession(); s.begin(h);
            assertTrue(s.execute(action)); String after = h.text;
            assertTrue(s.execute(EditorAction.UNDO)); assertEquals(original, h.text);
            assertEquals(10, h.start); assertEquals(7, h.end);
            assertTrue(s.execute(EditorAction.REDO)); assertEquals(after, h.text);
        }
    }
    @Test public void externalEditsAndCursorMovesCannotBeReversedByFallback() {
        for (boolean move : new boolean[] {false, true}) {
            Host h = new Host(); EditorActionSession s = new EditorActionSession(); s.begin(h);
            s.execute(EditorAction.PASTE);
            if (move) h.start = h.end = 0; else h.text += "external";
            String before = h.text;
            assertFalse(s.execute(EditorAction.UNDO)); assertEquals(before, h.text); assertEquals(0, h.commits);
        }
    }
    @Test public void nativeUnexpectedMutationNeverTriggersSecondEdit() {
        Host h = new Host(); EditorActionSession s = new EditorActionSession(); s.begin(h);
        s.execute(EditorAction.PASTE); h.nativeNoOp = false;
        assertTrue(s.execute(EditorAction.UNDO)); assertEquals(0, h.commits);
    }
    @Test public void sensitivePartialAndReplacedSessionsHaveNoLocalHistory() {
        for (int mode = 0; mode < 3; mode++) {
            Host h = new Host(); h.privateField = mode == 0; h.partial = mode == 1;
            EditorActionSession s = new EditorActionSession(); s.begin(h);
            s.execute(EditorAction.PASTE);
            if (mode == 2) { s.end(); s.begin(h); }
            s.execute(EditorAction.UNDO); assertEquals(0, h.commits);
        }
    }
    @Test public void observedHostUndoWorksWithoutLocalJournal() {
        Host h = new Host(); h.nativeNoOp=false;
        EditorActionSession s = new EditorActionSession(); s.begin(h);
        assertTrue(s.execute(EditorAction.UNDO)); assertEquals(0,h.commits);
    }
    @Test public void observedNoOpDoesNotClaimNativeUndoChangedText() {
        Host h = new Host(); EditorActionSession s = new EditorActionSession(); s.begin(h);
        assertFalse(s.execute(EditorAction.UNDO)); assertEquals(0,h.commits);
    }
    @Test public void movingAwayAndBackOrStartingCompositionInvalidatesHistory() {
        for (boolean composition : new boolean[] {true,false}) {
            Host h = new Host(); EditorActionSession session = new EditorActionSession(); session.begin(h);
            session.execute(EditorAction.PASTE);
            int cursor=h.start;
            if (composition) session.onSelectionUpdated(cursor,cursor,0,cursor);
            else {
                session.onSelectionUpdated(0,0,-1,-1);
                session.onSelectionUpdated(cursor,cursor,-1,-1);
            }
            assertFalse(session.execute(EditorAction.UNDO)); assertEquals(0,h.commits);
        }
    }
    @Test public void expectedAsynchronousSelectionUpdateKeepsPasteJournal() {
        Host h = new Host(); EditorActionSession session = new EditorActionSession(); session.begin(h);
        session.execute(EditorAction.PASTE);
        session.onSelectionUpdated(h.start,h.end,-1,-1);
        assertTrue(session.execute(EditorAction.UNDO));
    }
    @Test public void unsupportedSnapshotDoesNotBlockNativePaste() {
        Host h = new Host(); EditorActionSession s = new EditorActionSession(); s.begin(h);
        h.onRead = () -> { throw new UnsupportedOperationException(); };
        assertTrue(s.execute(EditorAction.PASTE)); assertEquals(1,h.nativeCalls); assertEquals(0,h.commits);
    }
    @Test public void lateOwnIntermediateSelectionKeepsRedoOnlyWithProvenFinalEditorState() {
        Host h=new Host(); h.start=10;h.end=7;
        EditorActionSession s=new EditorActionSession();s.begin(h);
        s.execute(EditorAction.PASTE);s.execute(EditorAction.UNDO);
        s.onSelectionUpdated(7,7+h.clip.length(),-1,-1); // Our recorded selection before inverse commit.
        assertTrue(s.execute(EditorAction.REDO));
    }
    @Test public void superficiallyMatchingCallbackCannotHideAnExternalSelectionChange() {
        Host h=new Host();h.start=10;h.end=7;
        EditorActionSession s=new EditorActionSession();s.begin(h);
        s.execute(EditorAction.PASTE);s.execute(EditorAction.UNDO);
        h.start=h.end=1;
        s.onSelectionUpdated(7,7+h.clip.length(),-1,-1);
        h.start=10;h.end=7; // Coming back cannot recover the invalidated transaction.
        int commits=h.commits; assertFalse(s.execute(EditorAction.REDO));assertEquals(commits,h.commits);
    }
    @Test public void largeEditorAndExternalClipboardPastesNeverEnterLocalJournal() {
        Host h=new Host();h.text="x".repeat(32769);
        EditorActionSession s=new EditorActionSession();s.begin(h);
        s.execute(EditorAction.PASTE);s.execute(EditorAction.UNDO);assertEquals(0,h.commits);
        h=new Host();s.begin(h);h.replace(h.clip); // Native clipboard card/host insertion, outside toolbar.
        assertFalse(s.execute(EditorAction.UNDO));assertEquals(0,h.commits);
    }
    @Test public void reentrantSnapshotCannotMutateOldField() {
        Host h = new Host(); EditorActionSession s = new EditorActionSession(); s.begin(h);
        h.onRead = s::end;
        assertFalse(s.execute(EditorAction.PASTE)); assertEquals(0, h.nativeCalls);
    }
}
