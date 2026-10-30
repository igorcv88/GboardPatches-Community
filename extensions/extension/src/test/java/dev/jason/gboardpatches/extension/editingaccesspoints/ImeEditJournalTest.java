package dev.jason.gboardpatches.extension.editingaccesspoints;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImeEditJournalTest {
    private static ImeEditJournal.State s(String text, int cursor) { return new ImeEditJournal.State(text, cursor, cursor); }

    private static void edit(ImeEditJournal journal, ImeEditJournal.State before, ImeEditJournal.State after, long time) {
        journal.beforeEdit(before);
        journal.afterEdit(after, time);
    }

    @Test public void typedWordIsOneStepAndUndoRestoresPreviousText() {
        ImeEditJournal journal = new ImeEditJournal();
        String[] typed = {"", "h", "he", "hel", "hell", "hello"};
        for (int i = 1; i < typed.length; i++) edit(journal, s(typed[i - 1], i - 1), s(typed[i], i), i);
        assertEquals(1, journal.undoSize());
        ImeEditJournal.State current = s("hello", 5);
        ImeEditJournal.Edit step = journal.peekUndo(current);
        ImeEditJournal.State target = ImeEditJournal.target(current, step, true);
        assertEquals("", target.text);
        assertTrue(journal.applied(step, true, target, target));
        ImeEditJournal.Edit again = journal.peekRedo(target);
        ImeEditJournal.State redone = ImeEditJournal.target(target, again, false);
        assertEquals("hello", redone.text);
        assertEquals(5, redone.start);
    }

    @Test public void whitespaceSeparatesWordsAndAutocorrectStaysInItsWord() {
        ImeEditJournal journal = new ImeEditJournal();
        edit(journal, s("", 0), s("teh", 3), 0);
        edit(journal, s("teh", 3), s("the ", 4), 10); // Autocorrect on space rewrites the word.
        edit(journal, s("the ", 4), s("the c", 5), 20);
        edit(journal, s("the c", 5), s("the cat", 7), 30);
        assertEquals(2, journal.undoSize());
        ImeEditJournal.State current = s("the cat", 7);
        ImeEditJournal.State first = ImeEditJournal.target(current, journal.peekUndo(current), true);
        assertEquals("the ", first.text);
        assertTrue(journal.applied(journal.peekUndo(current), true, first, first));
        ImeEditJournal.State second = ImeEditJournal.target(first, journal.peekUndo(first), true);
        assertEquals("", second.text);
    }

    @Test public void consecutiveBackspacesGroupAndRestoreDeletedText() {
        ImeEditJournal journal = new ImeEditJournal();
        journal.beforeEdit(s("abc def", 7));
        journal.afterEdit(s("abc de", 6), 0);
        edit(journal, s("abc de", 6), s("abc d", 5), 1);
        edit(journal, s("abc d", 5), s("abc ", 4), 2);
        assertEquals(1, journal.undoSize());
        ImeEditJournal.State current = s("abc ", 4);
        ImeEditJournal.State target = ImeEditJournal.target(current, journal.peekUndo(current), true);
        assertEquals("abc def", target.text);
        assertEquals(7, target.start);
    }

    @Test public void externalChangeDiscardsHistory() {
        ImeEditJournal journal = new ImeEditJournal();
        edit(journal, s("", 0), s("hi", 2), 0);
        assertTrue(journal.canUndo(s("hi", 2)));
        assertFalse(journal.canUndo(s("hi!", 3)));      // App changed the text.
        assertNotNull(journal.peekUndo(s("hi", 0)));    // Caret movement does not alter text.
        journal.beforeEdit(s("hi!", 3));
        journal.afterEdit(s("hi!x", 4), 1);
        assertEquals(1, journal.undoSize());
        ImeEditJournal.State current = s("hi!x", 4);
        assertEquals("hi!", ImeEditJournal.target(current, journal.peekUndo(current), true).text);
    }

    @Test public void pasteStartsItsOwnStepAndCursorMovementKeepsOlderUndoAvailable() {
        ImeEditJournal journal = new ImeEditJournal();
        edit(journal, s("", 0), s("a", 1), 1);
        assertTrue(journal.recordObserved(s("a", 1), s("aPASTE", 6), 2));
        assertEquals(2, journal.undoSize());
        ImeEditJournal.State moved = s("aPASTE", 0);
        ImeEditJournal.Edit paste = journal.peekUndo(moved);
        ImeEditJournal.State unpasted = ImeEditJournal.target(moved, paste, true);
        assertEquals("a", unpasted.text);
        assertTrue(journal.applied(paste, true, unpasted, unpasted));
        assertEquals("", ImeEditJournal.target(unpasted, journal.peekUndo(unpasted), true).text);
    }

    @Test public void sameObservedPasteIsNotAddedTwice() {
        ImeEditJournal journal = new ImeEditJournal();
        edit(journal, s("", 0), s("pasted", 6), 1);
        assertTrue(journal.recordObserved(s("", 0), s("pasted", 6), 2));
        assertEquals(1, journal.undoSize());
    }

    @Test public void unreadableEditorAndMismatchedResultClearHistory() {
        ImeEditJournal journal = new ImeEditJournal();
        edit(journal, s("", 0), s("a", 1), 0);
        journal.beforeEdit(null);
        assertEquals(0, journal.undoSize());
        edit(journal, s("", 0), s("a", 1), 0);
        ImeEditJournal.State current = s("a", 1);
        ImeEditJournal.Edit step = journal.peekUndo(current);
        assertFalse(journal.applied(step, true, s("", 0), s("b", 0)));
        assertFalse(journal.canUndo(s("b", 0)));
        assertEquals(0, journal.undoSize());
    }

    @Test public void newEditAfterUndoDropsRedoAndStartsNewStep() {
        ImeEditJournal journal = new ImeEditJournal();
        edit(journal, s("", 0), s("one", 3), 0);
        ImeEditJournal.State current = s("one", 3);
        ImeEditJournal.Edit step = journal.peekUndo(current);
        ImeEditJournal.State target = ImeEditJournal.target(current, step, true);
        journal.applied(step, true, target, target);
        edit(journal, target, s("two", 3), 1);
        assertEquals(0, journal.redoSize());
        assertEquals(1, journal.undoSize());
    }

    @Test public void pauseLongerThanWindowStartsNewStep() {
        ImeEditJournal journal = new ImeEditJournal();
        edit(journal, s("", 0), s("a", 1), 0);
        edit(journal, s("a", 1), s("ab", 2), ImeEditJournal.GROUP_WINDOW_MS + 1);
        assertEquals(2, journal.undoSize());
    }

    @Test public void diffNeverSplitsSurrogatePairs() {
        ImeEditJournal.Edit edit = ImeEditJournal.diff(s("x😀", 3), s("x😁", 3), 0);
        assertEquals(1, edit.offset);
        assertEquals("😀", edit.removed);
        assertEquals("😁", edit.inserted);
    }

    @Test public void historyIsBounded() {
        ImeEditJournal journal = new ImeEditJournal();
        String text = "";
        for (int i = 0; i < ImeEditJournal.MAX_ENTRIES + 20; i++) {
            journal.breakGroup();
            edit(journal, s(text, text.length()), s(text + "w ", text.length() + 2), i);
            text += "w ";
        }
        assertEquals(ImeEditJournal.MAX_ENTRIES, journal.undoSize());
    }
}
