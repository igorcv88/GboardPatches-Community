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
    @Test public void searchCopiesTheScaledNativeGapAndKeepsAnIndependentHitArea() {
        ClipboardSearchUiAdapter ui = fixture();
        View next = new View(actions.getContext());
        actions.addView(next, new LinearLayout.LayoutParams(48, 48));
        next.layout(160, 0, 208, 48); // Native gap = 12; no dp/px offset in production.
        ui.alignToPeers();
        assertEquals(12, peer.getLeft() - ui.search.getRight());
        assertEquals(peer.getWidth(), ui.search.getWidth());
        root.bridge = true;
        event(android.view.MotionEvent.ACTION_DOWN, true);
        event(android.view.MotionEvent.ACTION_UP, true);
        assertEquals(1, picks);
        peer.layout(140, 0, 212, 72); next.layout(230, 0, 302, 72);
        ui.alignToPeers();
        assertEquals(18, peer.getLeft() - ui.search.getRight());
        assertEquals(72, ui.search.getHeight());
        ui.close();
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
        root.setBackgroundColor(0xff202124); // Dark keyboard header: the backdrop is known.
        actions = new LinearLayout(activity); root.addView(actions);
        peer = new View(activity); actions.addView(peer, new LinearLayout.LayoutParams(48, 48));
        ClipboardSearchUiAdapter ui = new ClipboardSearchUiAdapter(actions, () -> picks++);
        root.ui = ui;
        activity.setContentView(root);
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 400, 600);
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
        // Moving the IME window does not remeasure/reflow its already laid-out children.
        root.offsetLeftAndRight(30); root.offsetTopAndBottom(170);
        assertEquals(48, ui.search.getHeight());
        assertTrue(ui.search.getGlobalVisibleRect(new android.graphics.Rect()));
        event(android.view.MotionEvent.ACTION_DOWN, true); event(android.view.MotionEvent.ACTION_UP, true);
        assertEquals(1, picks);
        ui.close();
    }

    private static int disc(ClipboardSearchUiAdapter ui) {
        android.graphics.drawable.InsetDrawable background = (android.graphics.drawable.InsetDrawable) ui.search.getBackground();
        return ((android.graphics.drawable.GradientDrawable) background.getDrawable()).getColor().getDefaultColor();
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void aNativeCircleOnTheInnerContentIsReproducedWithItsInsetsAndRenderedColor() {
        ClipboardSearchUiAdapter ui = fixture();
        actions.removeView(peer);
        android.widget.FrameLayout nativeKey = new android.widget.FrameLayout(actions.getContext());
        View circleView = new View(actions.getContext()); nativeKey.addView(circleView);
        android.graphics.drawable.GradientDrawable nativeCircle = new android.graphics.drawable.GradientDrawable();
        nativeCircle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        nativeCircle.setColor(0xff596166); circleView.setBackground(nativeCircle);
        actions.addView(nativeKey); nativeKey.layout(100, 0, 148, 48); circleView.layout(3, 3, 45, 45);
        ui.alignToPeers();
        android.graphics.drawable.InsetDrawable background = (android.graphics.drawable.InsetDrawable) ui.search.getBackground();
        assertEquals(0xff596166, disc(ui));
        assertNotSame(nativeCircle, background.getDrawable());
        int expectedSize = Math.round(20 * actions.getResources().getDisplayMetrics().density);
        assertEquals(expectedSize, ui.search.getDrawable().getIntrinsicWidth());
        background.setBounds(0, 0, 48, 48);
        assertEquals(new android.graphics.Rect(3, 3, 45, 45), background.getDrawable().getBounds());
        ui.close();
    }

    /** The persistent white disc: gray only in a state the native key has and the search button lacks. */
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void stateDependentNativeTintIsReadInTheKeysOwnStateNotTheSearchButtons() {
        ClipboardSearchUiAdapter ui = fixture();
        android.widget.ImageView edit = new android.widget.ImageView(actions.getContext());
        edit.setId(0x7f0b0526);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(android.graphics.Color.WHITE); // Untinted native asset is white.
        edit.setBackground(circle);
        edit.setBackgroundTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_activated}, {}}, new int[]{0x665f6368, android.graphics.Color.WHITE}));
        edit.setActivated(true);
        edit.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
        actions.addView(edit); edit.layout(160, 0, 208, 48);
        ui.alignToPeers();
        assertFalse(ui.search.isActivated());
        int color = disc(ui); // Premultiplied storage rounds translucent channels slightly.
        assertEquals(0x66, android.graphics.Color.alpha(color));
        assertEquals(0x5f, android.graphics.Color.red(color), 3);
        assertEquals(0x63, android.graphics.Color.green(color), 3);
        assertEquals(0x68, android.graphics.Color.blue(color), 3);
        ui.close();
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void anOpaqueDiscTheLensCannotBeSeenOnIsNeverAccepted() {
        ClipboardSearchUiAdapter ui = fixture();
        android.widget.ImageView edit = new android.widget.ImageView(actions.getContext());
        edit.setId(0x7f0b0526);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(android.graphics.Color.WHITE);
        edit.setBackground(circle);
        edit.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
        actions.addView(edit); edit.layout(160, 0, 208, 48);
        ui.alignToPeers();
        assertEquals(0x1cffffff, disc(ui));
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888);
        ui.search.draw(new android.graphics.Canvas(bitmap));
        int lens = 0;
        for (int y = 0; y < 48; y++) for (int x = 0; x < 48; x++) {
            int pixel = bitmap.getPixel(x, y);
            if (android.graphics.Color.alpha(pixel) > 200 && android.graphics.Color.red(pixel) > 230) lens++;
        }
        assertTrue("lens is drawn above the disc", lens > 5);
        assertTrue(android.graphics.Color.alpha(bitmap.getPixel(4, 24)) < 0x40);
        bitmap.recycle(); ui.close();
    }

    /** Contrast measured on the real composite: what the header shows, not a transparent bitmap. */
    private static double renderedLensContrast(View header, ClipboardSearchUiAdapter ui) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(header.getWidth(), header.getHeight(),
                android.graphics.Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(android.graphics.Color.WHITE); // Worst case behind a light theme.
        header.draw(new android.graphics.Canvas(bitmap));
        int[] at = new int[2], base = new int[2];
        ui.search.getLocationInWindow(at); header.getLocationInWindow(base);
        int left = at[0] - base[0], top = at[1] - base[1];
        int w = ui.search.getWidth(), h = ui.search.getHeight();
        int disc = bitmap.getPixel(left + w / 2, top + h / 2 + h / 3); // Inside the disc, below the lens.
        double best = 1;
        for (int y = top; y < top + h; y++) for (int x = left; x < left + w; x++) {
            best = Math.max(best, ClipboardNativeSkin.contrast(bitmap.getPixel(x, y), disc));
        }
        bitmap.recycle();
        return best;
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void aFaintWhiteDiscOnALightHeaderIsReplacedSoTheWhiteLensStaysLegible() {
        ClipboardSearchUiAdapter ui = fixture();
        root.setBackgroundColor(0xfff1f3f4); // Light theme header.
        android.widget.ImageView edit = new android.widget.ImageView(actions.getContext());
        edit.setId(0x7f0b0526);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(0x1cffffff); // 11 % white: alpha alone used to pass the old check.
        edit.setBackground(circle);
        edit.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
        actions.addView(edit); edit.layout(160, 0, 208, 48);
        ui.alignToPeers();
        int disc = disc(ui);
        assertNotEquals(0x1cffffff, disc);
        assertEquals(0, disc & 0x00ffffff); // A gray (black-toned) translucent disc under the white lens.
        assertTrue(android.graphics.Color.alpha(disc) < 0xff);
        assertTrue(ClipboardNativeSkin.renderedContrast(android.graphics.Color.WHITE, disc, ui.backdropColor())
                >= ClipboardNativeSkin.MIN_CONTRAST);
        assertTrue("lens legible in the drawn header", renderedLensContrast(root, ui) >= ClipboardNativeSkin.MIN_CONTRAST);
        ui.close();
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void aLegibleNativeDiscOnTheDarkHeaderIsKeptExactly() {
        ClipboardSearchUiAdapter ui = fixture();
        android.widget.ImageView edit = new android.widget.ImageView(actions.getContext());
        edit.setId(0x7f0b0526);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(0x1cffffff);
        edit.setBackground(circle);
        edit.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
        actions.addView(edit); edit.layout(160, 0, 208, 48);
        ui.alignToPeers();
        assertEquals(0x1c, android.graphics.Color.alpha(disc(ui)));
        assertEquals(0xffffff, disc(ui) & 0xffffff, 0x030303);
        assertTrue(renderedLensContrast(root, ui) >= ClipboardNativeSkin.MIN_CONTRAST);
        ui.close();
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void lensIsSizedAndColoredLikeTheRenderedNativeGlyph() {
        ClipboardSearchUiAdapter ui = fixture();
        android.widget.ImageView edit = new android.widget.ImageView(actions.getContext());
        edit.setId(0x7f0b0526);
        android.graphics.drawable.ShapeDrawable glyph = new android.graphics.drawable.ShapeDrawable();
        glyph.setIntrinsicWidth(30); glyph.setIntrinsicHeight(30);
        glyph.getPaint().setColor(android.graphics.Color.BLACK);
        edit.setImageDrawable(glyph);
        edit.setColorFilter(0xffe8eaed); // Vector/filter tint is only visible when rendered.
        edit.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        actions.addView(edit); edit.layout(160, 0, 208, 48);
        ui.alignToPeers();
        assertEquals(30, ui.search.getDrawable().getIntrinsicWidth());
        assertEquals(0xffe8eaed, ((ClipboardSearchUiAdapter.SearchIcon) ui.search.getDrawable()).foreground());
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

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void fallbackCircleUsesTheActualNativeGlyphTintInsteadOfTheApplicationTheme() {
        ClipboardSearchUiAdapter ui = fixture();
        actions.removeView(peer);
        android.widget.ImageView icon = new android.widget.ImageView(actions.getContext());
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
        actions.addView(icon); icon.layout(100, 0, 148, 48); ui.alignToPeers();
        android.graphics.drawable.InsetDrawable background = (android.graphics.drawable.InsetDrawable) ui.search.getBackground();
        int color = ((android.graphics.drawable.GradientDrawable) background.getDrawable()).getColor().getDefaultColor();
        assertEquals(0x1cffffff, color);
        ui.close();
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void editTemplateKeepsGraySkinWhiteLensAndNativePressFeedback() {
        ClipboardSearchUiAdapter ui = fixture();
        android.widget.ImageView edit = new android.widget.ImageView(actions.getContext());
        edit.setId(0x7f0b0526);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(android.graphics.Color.WHITE); // Untinted native resource is white.
        edit.setBackground(circle);
        edit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x665f6368));
        edit.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
        edit.setForeground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x44ffffff), null, null));
        actions.addView(edit); edit.layout(160, 0, 208, 48);
        ui.alignToPeers();
        assertNull(ui.search.getBackgroundTintList()); assertNull(ui.search.getImageTintList());
        assertEquals(android.graphics.Color.WHITE, ((ClipboardSearchUiAdapter.SearchIcon)ui.search.getDrawable()).foreground());
        assertNotSame(edit.getForeground(), ui.search.getForeground());
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888);
        ui.search.getBackground().setBounds(0, 0, 48, 48);
        ui.search.getBackground().draw(new android.graphics.Canvas(bitmap));
        int center = bitmap.getPixel(24, 24);
        assertTrue(android.graphics.Color.alpha(center) < 200);
        assertEquals(0x5f, android.graphics.Color.red(center), 2);
        ui.search.setPressed(true); ui.search.refreshDrawableState();
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT);
        ui.search.getBackground().draw(new android.graphics.Canvas(bitmap));
        assertEquals(center, bitmap.getPixel(24, 24));
        ui.search.setPressed(false); ui.search.refreshDrawableState();
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT);
        ui.search.getBackground().draw(new android.graphics.Canvas(bitmap));
        assertEquals(center, bitmap.getPixel(24, 24));
        bitmap.recycle(); ui.close();
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void aTransparentWhiteNativeForegroundMustNotBecomeAnOpaqueCircleOverTheLens() {
        ClipboardSearchUiAdapter ui = fixture();
        android.widget.ImageView edit = new android.widget.ImageView(actions.getContext()); edit.setId(0x7f0b0526);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setShape(android.graphics.drawable.GradientDrawable.OVAL); background.setColor(0xff5f6368);
        background.setAlpha(102); edit.setBackground(background); edit.setAlpha(0.5f);
        android.graphics.drawable.GradientDrawable highlight = new android.graphics.drawable.GradientDrawable();
        highlight.setShape(android.graphics.drawable.GradientDrawable.OVAL); highlight.setColor(android.graphics.Color.WHITE);
        highlight.setAlpha(0); edit.setForeground(highlight);
        edit.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
        actions.addView(edit); edit.layout(160, 0, 208, 48); ui.alignToPeers();
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888);
        ui.search.draw(new android.graphics.Canvas(bitmap));
        int centerAlpha = android.graphics.Color.alpha(bitmap.getPixel(24, 24));
        assertTrue("native gray backdrop is translucent", centerAlpha < 150);
        int lens = 0;
        for (int y = 0; y < 48; y++) for (int x = 0; x < 48; x++) {
            int pixel = bitmap.getPixel(x, y);
            if (android.graphics.Color.alpha(pixel) > 200 && android.graphics.Color.red(pixel) > 230) lens++;
        }
        assertTrue("white lens is visible above the native backdrop", lens > 5);
        ui.search.setPressed(true);
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT);
        ui.search.draw(new android.graphics.Canvas(bitmap));
        assertTrue("press feedback darkens/lightens only the disc", android.graphics.Color.alpha(bitmap.getPixel(24, 6)) > centerAlpha);
        ui.search.setPressed(false);
        assertEquals(0, highlight.getAlpha()); assertEquals(102, background.getAlpha()); // Native controls untouched.
        bitmap.recycle(); ui.close();
    }

}
