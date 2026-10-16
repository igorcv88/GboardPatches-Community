package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 33})
public class EditorStyledSnapshotTest {
    private SpannedString styled() {
        SpannableString text = new SpannableString("link bold");
        text.setSpan(new URLSpan("https://example.test"),0,4,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(1),5,9,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return new SpannedString(text);
    }
    @Test public void separatelyReconstructedSpannedStringsHaveEqualStableDescriptors() {
        SpannedString first=styled(), second=styled();
        assertNotSame(first.getSpans(0,4,URLSpan.class)[0],second.getSpans(0,4,URLSpan.class)[0]);
        assertEquals(StableEditorSpans.describe(first),StableEditorSpans.describe(second));
    }
}
