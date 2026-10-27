package dev.jason.gboardpatches.extension.clipboard;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 33}, manifest = Config.NONE)
public class ClipboardSearchUiAdapterTest {
    @Test public void headerGetsOnlyASearchButtonAndCloseRemovesIt() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout actions = new LinearLayout(activity);
        View stock = new View(activity); actions.addView(stock);
        int[] searches = {0};
        ClipboardSearchUiAdapter ui = new ClipboardSearchUiAdapter(actions, () -> searches[0]++);
        assertSame(ui.search, actions.getChildAt(0));
        assertSame(stock, actions.getChildAt(1));
        for (int i = 0; i < actions.getChildCount(); i++) assertFalse(actions.getChildAt(i) instanceof EditText);
        ui.search.performClick();
        assertEquals(1, searches[0]);
        ui.close(); ui.close();
        assertFalse(ui.attached());
        assertEquals(1, actions.getChildCount());
        ui.search.performClick();
        assertEquals(1, searches[0]);
    }
    static final class NativeRoot extends android.widget.FrameLayout {
        ClipboardSearchUiAdapter ui;
        boolean bridge;
        int stockEvents;
        NativeRoot(Activity activity) { super(activity); }
        @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
            if (bridge && ui != null && ui.dispatchTouch(this, event)) return true;
            stockEvents++; return true; // Native key graph consumes the event; child onClick never runs.
        }
    }
    private NativeRoot root;
    private LinearLayout actions;
    private View peer;
    private int picks;
    private ClipboardSearchUiAdapter fixture() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        root = new NativeRoot(activity);
        actions = new LinearLayout(activity); root.addView(actions);
        peer = new View(activity); actions.addView(peer, new LinearLayout.LayoutParams(48, 48));
        ClipboardSearchUiAdapter ui = new ClipboardSearchUiAdapter(actions, () -> picks++);
        root.ui = ui;
        activity.setContentView(root);
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 400, 300);
        root.layout(0, 0, 400, 200); actions.layout(100, 0, 340, 60);
        // Simulate native scaled-key positioning leaving a gap before the first stock key.
        peer.layout(100, 0, 148, 48);
        ui.search.layout(0, 0, 40, 60); ui.alignToPeers();
        return ui;
    }
    private void event(int action, boolean inside) {
        int[] location = new int[2], source = new int[2];
        root.ui.search.getLocationInWindow(location); root.getLocationInWindow(source);
        float x = inside ? location[0] - source[0] + root.ui.search.getWidth() / 2f : location[0] - source[0] - 20;
        float y = location[1] - source[1] + root.ui.search.getHeight() / 2f;
        android.view.MotionEvent event = android.view.MotionEvent.obtain(0, 1, action, x, y, 0);
        root.dispatchTouchEvent(event); event.recycle();
    }
    @Test public void nativeTouchGraphCanActivateSearchExactlyOnceWhileOtherKeysRemainStock() {
        ClipboardSearchUiAdapter ui = fixture();
        event(android.view.MotionEvent.ACTION_DOWN, true); event(android.view.MotionEvent.ACTION_UP, true);
        assertEquals(0, picks); // An ordinary listener is insufficient on this parent.
        root.bridge = true;
        event(android.view.MotionEvent.ACTION_DOWN, true); event(android.view.MotionEvent.ACTION_UP, true);
        assertEquals(1, picks); assertEquals(2, root.stockEvents);
        event(android.view.MotionEvent.ACTION_DOWN, false); event(android.view.MotionEvent.ACTION_UP, false);
        assertEquals(1, picks); assertEquals(4, root.stockEvents);
        ui.close();
    }
    @Test public void cancelledDraggedAndDetachedGesturesNeverOpenSearch() {
        ClipboardSearchUiAdapter ui = fixture(); root.bridge = true;
        event(android.view.MotionEvent.ACTION_DOWN, true); event(android.view.MotionEvent.ACTION_CANCEL, true);
        event(android.view.MotionEvent.ACTION_DOWN, true); event(android.view.MotionEvent.ACTION_MOVE, false);
        event(android.view.MotionEvent.ACTION_UP, true);
        assertEquals(0, picks);
        ui.close(); event(android.view.MotionEvent.ACTION_UP, true);
        assertEquals(0, picks); assertFalse(ui.search.isPressed());
    }
    @Test public void vectorActionUsesPeerBoundsWithoutAGapAndRestoresParentClipping() {
        ClipboardSearchUiAdapter ui = fixture();
        assertEquals(peer.getLeft(), ui.search.getRight());
        assertEquals(peer.getWidth(), ui.search.getWidth());
        assertEquals(peer.getHeight(), ui.search.getHeight());
        assertTrue(ui.search.getDrawable() instanceof ClipboardSearchUiAdapter.SearchIcon);
        assertFalse(actions.getClipChildren());
        ui.close(); assertTrue(actions.getClipChildren());
    }

    @Test public void searchUsesTheNativePeerTintRatherThanASystemBitmapOrDefaultThemeColor() {
        ClipboardSearchUiAdapter ui = fixture();
        actions.removeView(peer);
        android.widget.ImageView nativeIcon = new android.widget.ImageView(actions.getContext());
        nativeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.CYAN));
        actions.addView(nativeIcon); nativeIcon.layout(100, 0, 148, 48);
        ui.alignToPeers();
        android.graphics.Paint paint = org.robolectric.util.ReflectionHelpers.getField(ui.search.getDrawable(), "paint");
        assertEquals(android.graphics.Color.CYAN, paint.getColor());
        ui.close();
    }

    @Test public void viewportCoordinatesWorkWhenTheKeyboardIsTranslatedAwayFromScreenOrigin() {
        ClipboardSearchUiAdapter ui = fixture(); root.bridge = true;
        root.layout(30, 170, 430, 370);
        event(android.view.MotionEvent.ACTION_DOWN, true); event(android.view.MotionEvent.ACTION_UP, true);
        assertEquals(1, picks);
        ui.close();
    }

    @Test public void aNativeCircleOnTheInnerContentIsCopiedWithItsInsetsAndASmallerLens() {
        ClipboardSearchUiAdapter ui = fixture();
        actions.removeView(peer);
        android.widget.FrameLayout nativeKey = new android.widget.FrameLayout(actions.getContext());
        View circleView = new View(actions.getContext()); nativeKey.addView(circleView);
        android.graphics.drawable.GradientDrawable nativeCircle = new android.graphics.drawable.GradientDrawable();
        nativeCircle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        nativeCircle.setColor(0xff596166); circleView.setBackground(nativeCircle);
        actions.addView(nativeKey); nativeKey.layout(100, 0, 148, 48); circleView.layout(3, 3, 45, 45);
        ui.alignToPeers();
        assertNotNull(ui.search.getBackground());
        android.graphics.drawable.InsetDrawable background = (android.graphics.drawable.InsetDrawable) ui.search.getBackground();
        assertTrue(background.getDrawable() instanceof android.graphics.drawable.GradientDrawable);
        android.graphics.drawable.GradientDrawable copied = (android.graphics.drawable.GradientDrawable) background.getDrawable();
        assertEquals(nativeCircle.getColor(), copied.getColor());
        assertNotSame(nativeCircle, copied);
        int expectedSize = Math.round(20 * actions.getResources().getDisplayMetrics().density);
        assertEquals(expectedSize, ui.search.getDrawable().getIntrinsicWidth());
        background.setBounds(0, 0, 48, 48);
        assertEquals(new android.graphics.Rect(3, 3, 45, 45), copied.getBounds());
        ui.close();
    }

    @Test public void aTransparentNativeRippleDoesNotLeaveTheSearchActionWithoutACircle() {
        ClipboardSearchUiAdapter ui = fixture();
        peer.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE), null, null));
        ui.alignToPeers();
        android.graphics.drawable.InsetDrawable background = (android.graphics.drawable.InsetDrawable) ui.search.getBackground();
        assertTrue(background.getDrawable() instanceof android.graphics.drawable.GradientDrawable);
        assertTrue(android.graphics.Color.alpha(((android.graphics.drawable.GradientDrawable) background.getDrawable()).getColor().getDefaultColor()) > 0);
        ui.close();
    }

    @Test public void aWideTouchTargetStillHasACircularBackgroundAndACenteredGlyph() {
        ClipboardSearchUiAdapter ui = fixture();
        peer.layout(100, 0, 172, 48); ui.alignToPeers();
        android.graphics.drawable.InsetDrawable background = (android.graphics.drawable.InsetDrawable) ui.search.getBackground();
        background.setBounds(0, 0, 72, 48);
        android.graphics.Rect circle = background.getDrawable().getBounds();
        assertEquals(circle.width(), circle.height());
        assertEquals(36, circle.centerX()); assertEquals(24, circle.centerY());
        assertEquals(12, ui.search.getPaddingLeft()); assertEquals(12, ui.search.getPaddingRight());
        ui.close();
    }

}
