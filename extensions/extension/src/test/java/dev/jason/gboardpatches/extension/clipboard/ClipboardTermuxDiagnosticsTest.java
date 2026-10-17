package dev.jason.gboardpatches.extension.clipboard;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class ClipboardTermuxDiagnosticsTest {
    public static class Helper {
        public EditorInfo m = new EditorInfo();
        public List<?> k = List.of();
        public Object i, j;
        public boolean q;
        Helper() { m.packageName = "com.termux"; }
        @Override public String toString() { throw new AssertionError("must not stringify native state"); }
    }
    @Test public void recordsOnlyNativePipelineBitsWithoutReadingOrChangingAnyClip() {
        Helper h = new Helper();
        Object secret = new Object() {
            @Override public String toString() { throw new AssertionError("must not read text"); }
        };
        h.k = List.of(secret); h.i = secret; h.j = secret; h.q = true;
        assertEquals(1 | 8 | 16 | 32 | 64, TermuxClipboardDiagnostics.snapshot(h));
        assertSame(secret, h.k.get(0)); assertSame(secret, h.i); assertSame(secret, h.j);
        assertTrue(h.q); assertEquals(InputType.TYPE_NULL, h.m.inputType);
    }
    @Test public void exactTermuxShapesOnlyIncludingNoPersonalizedLearning() {
        Helper h = new Helper();
        h.m.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        h.m.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        assertEquals(2 | 4, TermuxClipboardDiagnostics.snapshot(h));
        h.m.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        assertEquals(-1, TermuxClipboardDiagnostics.snapshot(h));
        h.m.inputType = InputType.TYPE_NULL; h.m.packageName = "com.termux.other";
        assertEquals(-1, TermuxClipboardDiagnostics.snapshot(h));
        h.m.packageName = "another.editor";
        assertEquals(-1, TermuxClipboardDiagnostics.snapshot(h));
    }
    @Test public void absentOrChangedNativeBindingsFailClosed() {
        assertEquals(-1, TermuxClipboardDiagnostics.snapshot(null));
        assertEquals(-1, TermuxClipboardDiagnostics.snapshot(new Object()));
        Helper h = new Helper(); h.m = null;
        assertEquals(-1, TermuxClipboardDiagnostics.snapshot(h));
    }
}
