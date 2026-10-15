package dev.jason.gboardpatches.extension.editingaccesspoints;

import org.junit.Test;
import static org.junit.Assert.*;

public class EditorEditTransactionTest {
    @Test public void insertionSelectionReplacementCutAndReverseSelection() {
        for (int[] range : new int[][] {{1, 1}, {1, 3}, {3, 1}}) {
            String before = "abcde";
            int low = Math.min(range[0], range[1]), high = Math.max(range[0], range[1]);
            for (String inserted : new String[] {"hello", "", " 😃\nç "}) {
                String after = before.substring(0, low) + inserted + before.substring(high);
                EditorEditTransaction t = EditorEditTransaction.capture(before, range[0], range[1],
                        after, low + inserted.length(), low + inserted.length());
                if (before.equals(after)) { assertNull(t); continue; }
                assertNotNull(t);
                assertTrue(t.matches(after, low + inserted.length(), low + inserted.length()));
                assertFalse(t.matches(after + "x", low + inserted.length(), low + inserted.length()));
                t.undone = true;
                assertTrue(t.matches(before, range[0], range[1]));
            }
        }
    }
    @Test public void rejectsUnprovenMutationsAndTruncatedOrOversizedSnapshots() {
        assertNull(EditorEditTransaction.capture("abc", 1, 1, "xinsertbc", 7, 7));
        assertNull(EditorEditTransaction.capture("abc", -1, 1, "abc", 1, 1));
        assertNull(EditorEditTransaction.capture("abc", 1, 1, "aXbc", 1, 1));
        assertNull(EditorEditTransaction.capture("x".repeat(32769), 0, 0, "a", 1, 1));
        assertNull(EditorEditTransaction.capture("😃a", 1, 1, "x", 1, 1));
        assertNull(EditorEditTransaction.capture("abc", 1, 1, "aXbc", 2, 3));
    }
    @Test public void selectionChangesInvalidateJournal() {
        EditorEditTransaction t = EditorEditTransaction.capture("abc", 1, 2, "aXXc", 3, 3);
        assertNotNull(t);
        assertFalse(t.matches("aXXc", 2, 2));
        t.undone = true;
        assertFalse(t.matches("abc", 2, 2));
    }
}
