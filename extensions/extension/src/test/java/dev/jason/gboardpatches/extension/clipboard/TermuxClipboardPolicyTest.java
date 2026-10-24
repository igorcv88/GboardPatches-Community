package dev.jason.gboardpatches.extension.clipboard;

import org.junit.Test;
import static org.junit.Assert.*;

public class TermuxClipboardPolicyTest {
    private boolean allowed(String text) {
        return TermuxClipboardPolicy.eligible("com.termux", 0, 0,
                false, false, false, true, text, 1000, 1100);
    }
    @Test public void acceptsShortTextUnicodeEmojiAndMultilineWithoutChangingText() {
        assertTrue(allowed("echo olá"));
        assertTrue(allowed("日本語 🐈‍⬛ café"));
        assertTrue(allowed("printf 'a'\nprintf 'b'\n"));
        assertFalse(TermuxClipboardPolicy.needsConfirmation("日本語 🐈‍⬛ café"));
        for (String text : new String[]{"a\nb", "a\rb", "a\tb", "a\u2028b", "a\u2029b"}) {
            assertTrue(TermuxClipboardPolicy.needsConfirmation(text));
        }
    }
    @Test public void rejectsEmptyAndTerminalControlSequences() {
        assertFalse(allowed(null)); assertFalse(allowed(""));
        for (String text : new String[]{"\u001b[201~\n", "a\u0003", "a\u0000", "a\u007f", "a\u0085"}) {
            assertFalse(allowed(text));
        }
        assertFalse(allowed("a".repeat(1_000_001)));
    }
    @Test public void exactPackageAndTypeOnly() {
        for (String pkg : new String[]{null, "com.termux.other", "ordinary.editor"}) {
            assertFalse(TermuxClipboardPolicy.eligible(pkg, 0, 0, false, false, false, true, "a", 1000, 1100));
        }
        assertFalse(TermuxClipboardPolicy.eligible("com.termux", 1, 0, false, false, false, true, "a", 1000, 1100));
        assertFalse(TermuxClipboardPolicy.eligible("com.termux", 0x80091, 0, false, false, false, true, "a", 1000, 1100));
    }
    @Test public void refusesEachPrivacyAndLockGateIndependently() {
        assertFalse(TermuxClipboardPolicy.eligible("com.termux", 0, 0x01000000, false, false, false, true, "a", 1000, 1100));
        assertFalse(TermuxClipboardPolicy.eligible("com.termux", 0, 0, true, false, false, true, "a", 1000, 1100));
        assertFalse(TermuxClipboardPolicy.eligible("com.termux", 0, 0, false, true, false, true, "a", 1000, 1100));
        assertFalse(TermuxClipboardPolicy.eligible("com.termux", 0, 0, false, false, true, true, "a", 1000, 1100));
        assertFalse(TermuxClipboardPolicy.eligible("com.termux", 0, 0, false, false, false, false, "a", 1000, 1100));
    }
    @Test public void matchesNativeTwoMinuteExpiryAndRejectsFutureTimestamp() {
        assertTrue(TermuxClipboardPolicy.eligible("com.termux", 0, 0, false, false, false, true, "a", 1000, 121000));
        assertFalse(TermuxClipboardPolicy.eligible("com.termux", 0, 0, false, false, false, true, "a", 1000, 121001));
        assertFalse(TermuxClipboardPolicy.eligible("com.termux", 0, 0, false, false, false, true, "a", 1000, 999));
    }
    @Test public void consumesOnlyOnceAndDoesNotRetryARejectedInsertion() {
        TermuxClipboardTicket ticket = new TermuxClipboardTicket();
        Object clip = new Object(); long id = ticket.offer(clip);
        assertTrue(ticket.claim(id));
        assertFalse(ticket.claim(id));
        assertFalse(ticket.active(ticket.offer(clip)));
    }
    @Test public void newCopyInvalidatesOldViewAndConfirmation() {
        TermuxClipboardTicket ticket = new TermuxClipboardTicket();
        long first = ticket.offer(new Object());
        long second = ticket.offer(new Object());
        assertFalse(ticket.claim(first)); assertTrue(ticket.claim(second));
    }
    @Test public void leavingEditorInvalidatesPendingTapAndAllowsNextSession() {
        TermuxClipboardTicket ticket = new TermuxClipboardTicket();
        Object clip = new Object(); long first = ticket.offer(clip);
        ticket.invalidate(); assertFalse(ticket.claim(first));
        assertTrue(ticket.claim(ticket.offer(clip)));
    }
}
