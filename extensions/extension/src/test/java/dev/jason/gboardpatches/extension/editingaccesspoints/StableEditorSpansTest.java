package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;

public class StableEditorSpansTest {
    static Spanned text(Object[] spans, int flags) {
        return (Spanned) Proxy.newProxyInstance(StableEditorSpansTest.class.getClassLoader(),
                new Class<?>[] {Spanned.class}, (p,m,a) -> {
            switch(m.getName()) {
                case "getSpans": return spans;
                case "getSpanStart": return 0;
                case "getSpanEnd": return 4;
                case "getSpanFlags": return flags;
                case "length": return 4;
                case "charAt": return "text".charAt((Integer)a[0]);
                case "subSequence": return "text".subSequence((Integer)a[0],(Integer)a[1]);
                case "toString": return "text";
            }
            return null;
        });
    }
    @Test public void reparceledStyleAndUrlValuesCompareEqualAndIgnoreOrder() {
        Spanned first=text(new Object[]{new StyleSpan(1),new URLSpan("https://example.test")},33);
        Spanned second=text(new Object[]{new URLSpan("https://example.test"),new StyleSpan(1)},33);
        assertEquals(StableEditorSpans.describe(first),StableEditorSpans.describe(second));
    }
    @Test public void styleValueFlagsAndDuplicateMultiplicityAreSignificant() {
        Object[] one={new StyleSpan(1)};
        assertNotEquals(StableEditorSpans.describe(text(one,33)),StableEditorSpans.describe(text(new Object[]{new StyleSpan(2)},33)));
        assertNotEquals(StableEditorSpans.describe(text(one,33)),StableEditorSpans.describe(text(one,34)));
        assertNotEquals(StableEditorSpans.describe(text(one,33)),StableEditorSpans.describe(text(new Object[]{new StyleSpan(1),new StyleSpan(1)},33)));
    }
    @Test public void unknownOrComposingSpansRejectLocalHistory() {
        assertNull(StableEditorSpans.describe(text(new Object[]{new Object()},33)));
        assertNull(StableEditorSpans.describe(text(new Object[]{new StyleSpan(1)},Spanned.SPAN_COMPOSING)));
        assertTrue(StableEditorSpans.describe("text").isEmpty());
    }
}
