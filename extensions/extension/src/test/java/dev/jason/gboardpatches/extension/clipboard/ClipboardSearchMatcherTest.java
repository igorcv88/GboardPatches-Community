package dev.jason.gboardpatches.extension.clipboard;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClipboardSearchMatcherTest {
    @Test public void accentsCaseWhitespaceAndWordsAnywhere() {
        assertTrue(new ClipboardSearchMatcher("SAO \n medicação").matches("medicação\nSão Paulo", () -> false));
        assertTrue(new ClipboardSearchMatcher("STRASSE").matches("Straße", () -> false));
        assertFalse(new ClipboardSearchMatcher("sao ausente").matches("São Paulo", () -> false));
    }
    @Test public void linksCodesAndEmojisAcrossChunkBoundary() {
        String text = "x".repeat(2047) + "😃https://example.test/a?b=2\nABC-123";
        assertTrue(new ClipboardSearchMatcher("😃https://example.test ABC-123").matches(text, () -> false));
        assertTrue(new ClipboardSearchMatcher("abcdef").matches("x".repeat(2045)+"abcdef", () -> false));
    }
    @Test public void fullLongClipAndCancellation() {
        assertTrue(new ClipboardSearchMatcher("final").matches("x".repeat(199995)+"FINAL", () -> false));
        assertFalse(new ClipboardSearchMatcher("final").matches("x".repeat(200000), () -> true));
        assertFalse(new ClipboardSearchMatcher("a").matches(null, () -> false));
        assertTrue(new ClipboardSearchMatcher(null).empty());
        assertTrue(new ClipboardSearchMatcher(" ").matches("", () -> false));
    }
    @Test public void greekSigmaAndCaselessExpansionsDoNotDependOnChunkAlignment() {
        for (int offset : new int[]{0,2046,2047,2048,4095}) {
            for(String word:new String[]{"ΟΣ ","ος ","οσ "}) {
                assertTrue(new ClipboardSearchMatcher("ΟΣ").matches("x".repeat(offset)+word,()->false));
                assertTrue(new ClipboardSearchMatcher("ος").matches("x".repeat(offset)+word,()->false));
            }
            assertTrue(new ClipboardSearchMatcher("straße").matches("x".repeat(offset)+"STRASSE",()->false));
            assertTrue(new ClipboardSearchMatcher("cafe").matches("x".repeat(offset)+"cafe\u0301",()->false));
        }
    }
    @Test public void queryIsBoundedAndRepeatedTermsDoNotBlock() {
        assertTrue(new ClipboardSearchMatcher("a a").matches("a", () -> false));
        assertTrue(new ClipboardSearchMatcher("a".repeat(300)).matches("a".repeat(256), () -> false));
    }
}
