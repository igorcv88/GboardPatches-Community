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

    /** Device layout: InputView spans the window and draws the keyboard at its bottom. */
    private FrameLayout keyboardRoot;
    private Activity setupFullHeightInput(int keyboardHeight) {
        Activity activity = setup();
        input.removeAllViews();
        FrameLayout inputView = new FrameLayout(activity);
        input.addView(inputView, new FrameLayout.LayoutParams(-1, -1));
        keyboardRoot = new FrameLayout(activity);
        keyboardRoot.setTag(ClipboardSearchWindowSlot.KEYBOARD_TAGS[0]);
        inputView.addView(keyboardRoot, new FrameLayout.LayoutParams(-1, keyboardHeight, android.view.Gravity.BOTTOM));
        layout();
        return activity;
    }

    /** Relative to the IME root; the test Activity's window adds its own offset. */
    private int top(View view) {
        int[] at = new int[2], base = new int[2];
        view.getLocationInWindow(at); root.getLocationInWindow(base);
        return at[1] - base[1];
    }

    private void settle(ClipboardSearchWindowSlot slot) {
        for (int i = 0; i < 5 && slot.anchorToKeyboard(); i++) layout();
        layout();
        assertFalse("anchoring reaches a fixed point", slot.anchorToKeyboard());
    }

    @Test public void surfaceBottomFollowsTheKeyboardTopWithoutAGap() {
        Activity activity = setupFullHeightInput(300);
        ClipboardSearchWindowSlot slot = new ClipboardSearchWindowSlot(candidates, fullscreen);
        View surface = new View(activity);
        slot.attach(surface, 240); layout();
        assertEquals(0, top(surface)); // The reported gap: surface at the window top.
        assertEquals(600, top(keyboardRoot));
        settle(slot);
        assertEquals(240, surface.getHeight());
        assertEquals(top(keyboardRoot), top(surface) + surface.getHeight());
        assertEquals(600, top(keyboardRoot)); // Keyboard stays where it was.
        assertEquals(300, input.getHeight()); // inputArea measures exactly to the keyboard.

        // Taller keyboard (e.g. toolbar or height setting): the surface moves with it.
        keyboardRoot.getLayoutParams().height = 420; keyboardRoot.requestLayout(); layout();
        settle(slot);
        assertEquals(top(keyboardRoot), top(surface) + surface.getHeight());
        assertEquals(240, surface.getHeight());

        // Little room left: the list is limited to the available space, never overlaps.
        keyboardRoot.getLayoutParams().height = 780; keyboardRoot.requestLayout(); layout();
        settle(slot);
        assertEquals(120, surface.getHeight());
        assertEquals(top(keyboardRoot), top(surface) + surface.getHeight());

        slot.close(); layout();
        assertNull(surface.getParent()); assertEquals(0, candidates.getChildCount());
        assertEquals(900, input.getHeight());
        assertEquals(View.INVISIBLE, fullscreen.getVisibility());
    }

    @Test public void surfaceTakesTheKeyboardsHorizontalExtent() {
        Activity activity = setupFullHeightInput(300);
        FrameLayout.LayoutParams narrow = (FrameLayout.LayoutParams) keyboardRoot.getLayoutParams();
        narrow.width = 360; narrow.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        keyboardRoot.setLayoutParams(narrow); layout();
        ClipboardSearchWindowSlot slot = new ClipboardSearchWindowSlot(candidates, fullscreen);
        View surface = new View(activity);
        slot.attach(surface, 240); layout(); settle(slot);
        int[] s = new int[2], k = new int[2];
        surface.getLocationInWindow(s); keyboardRoot.getLocationInWindow(k);
        assertEquals(k[0], s[0]); assertEquals(360, surface.getWidth());
        slot.close();
    }

    @Test public void withoutANativeKeyboardRootTheBoundedSlotIsLeftAsBefore() {
        Activity activity = setup();
        ClipboardSearchWindowSlot slot = new ClipboardSearchWindowSlot(candidates, fullscreen);
        View surface = new View(activity);
        slot.attach(surface, 240); layout();
        assertFalse(slot.anchorToKeyboard());
        assertEquals(240, surface.getHeight()); assertEquals(660, input.getHeight());
        slot.close(); slot.close();
        assertFalse(slot.anchorToKeyboard());
    }
}
