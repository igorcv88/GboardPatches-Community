package dev.jason.gboardpatches.extension.clipboard;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 33}, manifest = Config.NONE)
public class ClipboardSearchWindowSlotTest {
    private LinearLayout root, fullscreen;
    private FrameLayout candidates, input;
    private View keyboard;

    private Activity setup() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL);
        fullscreen = new LinearLayout(activity); fullscreen.setOrientation(LinearLayout.VERTICAL);
        candidates = new FrameLayout(activity); fullscreen.addView(candidates, new LinearLayout.LayoutParams(-1, -2));
        root.addView(fullscreen, new LinearLayout.LayoutParams(-1, -2));
        input = new FrameLayout(activity); keyboard = new View(activity); input.addView(keyboard, new FrameLayout.LayoutParams(-1, -1));
        root.addView(input, new LinearLayout.LayoutParams(-1, 0, 1));
        fullscreen.setVisibility(View.INVISIBLE); candidates.setVisibility(View.INVISIBLE);
        activity.setContentView(root); layout(); return activity;
    }

    private void layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 480, 900);
    }

    @Test public void oldInvisiblePlaceholderReproducesZeroHeightKeyboard() {
        Activity activity = setup();
        assertEquals(900, input.getHeight());
        candidates.addView(new View(activity), new FrameLayout.LayoutParams(-1, -2));
        layout();
        assertEquals(900, candidates.getHeight());
        assertEquals(0, input.getHeight()); // Same failure as the Galaxy dump.
    }

    @Test public void boundedSearchAndRepeatedRestorationNeverStarveOrReplaceTheNativeInput() {
        Activity activity = setup();
        for (int i = 0; i < 30; i++) {
            ClipboardSearchWindowSlot slot = new ClipboardSearchWindowSlot(candidates, fullscreen);
            View surface = new View(activity);
            slot.attach(surface, 240); layout();
            assertTrue(surface.isShown()); assertEquals(240, surface.getHeight());
            assertEquals(660, input.getHeight()); assertSame(input, keyboard.getParent());
            slot.close(); slot.close(); layout();
            assertEquals(0, candidates.getChildCount()); assertNull(surface.getParent());
            assertEquals(View.INVISIBLE, fullscreen.getVisibility());
            assertEquals(View.INVISIBLE, candidates.getVisibility());
            assertEquals(900, input.getHeight()); assertSame(input, keyboard.getParent());
        }
    }

    @Test public void priorChildrenAndTheirExactLayoutAreRestored() {
        Activity activity = setup();
        View original = new View(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, 40);
        candidates.addView(original, params); layout();
        int originalInputHeight = input.getHeight();
        ClipboardSearchWindowSlot slot = new ClipboardSearchWindowSlot(candidates, fullscreen);
        slot.attach(new View(activity), 240); layout();
        slot.close(); layout();
        assertSame(original, candidates.getChildAt(0)); assertSame(params, original.getLayoutParams());
        assertEquals(originalInputHeight, input.getHeight());
    }
}
