package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

/** A vector action inside the same circular background as the native clipboard actions.
 * InputView's touch interceptor precedes child dispatch; search gestures are handled there,
 * using its local coordinate space. Other keyboard touches remain entirely native. */
public final class ClipboardSearchUiAdapter implements AutoCloseable {
    final ImageButton search;
    private final ViewGroup actions;
    private final boolean clipChildren, clipPadding;
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener align = this::alignToPeers;
    private boolean closed, captured, cancelled;
    private int pointer = -1;
    private Drawable nativeBackground;
    private final Rect backgroundBounds = new Rect();
    private int backgroundWidth, backgroundHeight, backgroundState;
    private final java.util.Map<Drawable, Boolean> paintedBackgrounds = new java.util.WeakHashMap<>();

    public ClipboardSearchUiAdapter(ViewGroup actions, Runnable onSearch) {
        this.actions = actions;
        Context context = actions.getContext();
        search = new ImageButton(context);
        TypedArray colors = context.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        int color;
        try { color = colors.getColor(0, 0xffdadce0); } finally { colors.recycle(); }
        search.setImageDrawable(new SearchIcon(color, Math.round(20 * context.getResources().getDisplayMetrics().density)));
        search.setContentDescription("Search clipboard");
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setPadding(0, 0, 0, 0);
        search.setMinimumWidth(0); search.setMinimumHeight(0);
        search.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        search.setOnClickListener(v -> { if (!closed) onSearch.run(); });
        clipChildren = actions.getClipChildren(); clipPadding = actions.getClipToPadding();
        actions.setClipChildren(false); actions.setClipToPadding(false);
        actions.addView(search, 0, new LinearLayout.LayoutParams(
                Math.round(40 * context.getResources().getDisplayMetrics().density), -1));
        actions.getViewTreeObserver().addOnGlobalLayoutListener(align);
    }

    /** Uses the rendered peer size/position, including native keyboard scaling and hidden keys. */
    void alignToPeers() {
        if (closed) return;
        View peer = null;
        for (int i = 0; i < actions.getChildCount(); i++) {
            View child = actions.getChildAt(i);
            if (child == search || child.getVisibility() != View.VISIBLE || child.getWidth() <= 0 || child.getHeight() <= 0) continue;
            if (peer == null || child.getLeft() < peer.getLeft()) peer = child;
        }
        if (peer == null) return;
        copyNativeTint(peer);
        int width = peer.getWidth(), height = peer.getHeight();
        int right = peer.getLeft(), top = peer.getTop();
        if (search.getLeft() != right - width || search.getTop() != top
                || search.getWidth() != width || search.getHeight() != height) {
            search.layout(right - width, top, right, top + height);
        }
        copyNativeBackground(peer);
    }

    /** The visible circle is often on the SoftKeyView content, not the outer key frame. */
    private void copyNativeBackground(View peer) {
        View backgroundView = findBackground(peer);
        Drawable source = backgroundView == null ? null : backgroundView.getBackground();
        Rect relative = new Rect(0, 0, peer.getWidth(), peer.getHeight());
        if (backgroundView != null && backgroundView != peer && peer instanceof ViewGroup group) {
            relative.set(0, 0, backgroundView.getWidth(), backgroundView.getHeight());
            group.offsetDescendantRectToMyCoords(backgroundView, relative);
        }
        int state = source == null ? 0 : java.util.Arrays.hashCode(backgroundView.getDrawableState());
        if (nativeBackground == source && backgroundBounds.equals(relative)
                && backgroundWidth == peer.getWidth() && backgroundHeight == peer.getHeight()
                && backgroundState == state && search.getBackground() != null) return;
        nativeBackground = source; backgroundBounds.set(relative);
        backgroundWidth = peer.getWidth(); backgroundHeight = peer.getHeight(); backgroundState = state;
        Drawable copy = null;
        if (source != null && source.getConstantState() != null) {
            copy = source.getConstantState().newDrawable(search.getResources()).mutate();
            copy.setState(backgroundView.getDrawableState()); copy.setAlpha(source.getAlpha());
            if (source.getColorFilter() != null) copy.setColorFilter(source.getColorFilter());
        }
        if (copy == null) {
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            TypedArray colors = search.getContext().obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
            int foreground;
            try { foreground = colors.getColor(0, 0xffdadce0); } finally { colors.recycle(); }
            circle.setColor((foreground & 0x00ffffff) | 0x1c000000);
            copy = circle;
            int diameter = Math.min(peer.getWidth(), peer.getHeight());
            int left = (peer.getWidth() - diameter) / 2, top = (peer.getHeight() - diameter) / 2;
            relative.set(left, top, left + diameter, top + diameter);
        }
        search.setBackground(new android.graphics.drawable.InsetDrawable(copy,
                relative.left, relative.top, Math.max(0, peer.getWidth() - relative.right),
                Math.max(0, peer.getHeight() - relative.bottom)));
        search.setPadding(Math.max(0, relative.left), Math.max(0, relative.top),
                Math.max(0, peer.getWidth() - relative.right), Math.max(0, peer.getHeight() - relative.bottom));
    }

    private View findBackground(View view) {
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE || child.getWidth() <= 0 || child.getHeight() <= 0) continue;
                View found = findBackground(child);
                if (found != null) return found;
            }
        }
        Drawable background = view.getBackground();
        if (background == null || background.getOpacity() == PixelFormat.TRANSPARENT) return null;
        if (background instanceof android.graphics.drawable.ColorDrawable color) return Color.alpha(color.getColor()) > 0 ? view : null;
        Boolean painted = paintedBackgrounds.get(background);
        if (painted == null) {
            painted = paintsAtRest(background, view.getDrawableState());
            paintedBackgrounds.put(background, painted);
        }
        return painted ? view : null;
    }

    private static boolean paintsAtRest(Drawable source, int[] state) {
        if (source == null || source.getAlpha() == 0) return false;
        if (source instanceof android.graphics.drawable.GradientDrawable gradient) {
            android.content.res.ColorStateList color = gradient.getColor();
            if (color != null) return Color.alpha(color.getColorForState(state, color.getDefaultColor())) > 0;
        }
        if (source instanceof android.graphics.drawable.LayerDrawable layers) {
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                if (layers.getId(i) != android.R.id.mask && paintsAtRest(layers.getDrawable(i), state)) return true;
            }
            return false;
        }
        if (source.getConstantState() == null) return false;
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888);
        try {
            Drawable probe = source.getConstantState().newDrawable().mutate();
            probe.setState(state); probe.setAlpha(source.getAlpha()); probe.setBounds(0, 0, 16, 16);
            probe.draw(new Canvas(bitmap));
            int[] pixels = new int[256]; bitmap.getPixels(pixels, 0, 16, 0, 0, 16, 16);
            for (int pixel : pixels) if (Color.alpha(pixel) > 0) return true;
            return false;
        } catch (Throwable ignored) { return false; }
        finally { bitmap.recycle(); }
    }

    private boolean copyNativeTint(View peer) {
        if (peer instanceof android.widget.ImageView image) {
            android.content.res.ColorStateList tint = image.getImageTintList();
            if (tint != null) {
                ((SearchIcon) search.getDrawable()).color(tint.getColorForState(image.getDrawableState(), tint.getDefaultColor()));
                return true;
            }
            ColorFilter filter = image.getColorFilter();
            if (filter == null && image.getDrawable() != null) filter = image.getDrawable().getColorFilter();
            if (filter != null) { search.getDrawable().setColorFilter(filter); return true; }
        }
        if (peer instanceof android.widget.TextView text && text.getText().length() > 0) {
            ((SearchIcon) search.getDrawable()).color(text.getCurrentTextColor()); return true;
        }
        if (peer instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE && copyNativeTint(child)) return true;
            }
        }
        return false;
    }

    private boolean contains(View source, MotionEvent event) {
        if (!attached() || !search.isShown() || !search.isEnabled() || !search.isAttachedToWindow()
                || search.getWidth() <= 0 || search.getHeight() <= 0) return false;
        Rect visible = new Rect();
        if (!search.getGlobalVisibleRect(visible)) return false;
        int[] root = new int[2], origin = new int[2];
        search.getRootView().getLocationInWindow(root);
        source.getLocationInWindow(origin);
        // getGlobalVisibleRect is in root coordinates; the MotionEvent is local to InputView.
        // Avoid raw screen coordinates, which the native dispatcher may retain/transform.
        visible.offset(root[0] - origin[0], root[1] - origin[1]);
        return visible.contains((int) event.getX(), (int) event.getY());
    }

    /** Consumes only a gesture started on search. Stock keys and drags are otherwise untouched. */
    boolean dispatchTouch(View keyboard, MotionEvent event) {
        if (closed || keyboard == null || event == null || keyboard.getRootView() != search.getRootView()) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            captured = event.getPointerCount() == 1 && contains(keyboard, event);
            cancelled = false;
            pointer = captured ? event.getPointerId(0) : -1;
            if (captured) {
                search.setPressed(true);
                android.util.Log.i("GboardPatches", "Clipboard search r12: touch-captured");
            }
            return captured;
        }
        if (!captured) return false;
        if (action == MotionEvent.ACTION_POINTER_DOWN || event.findPointerIndex(pointer) < 0) cancelled = true;
        if (action == MotionEvent.ACTION_MOVE && !contains(keyboard, event)) cancelled = true;
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            boolean click = action == MotionEvent.ACTION_UP && !cancelled && contains(keyboard, event);
            captured = false; pointer = -1; search.setPressed(false);
            if (click) search.performClick();
        }
        return true;
    }

    @Override public void close() {
        if (closed) return;
        closed = true; captured = false; search.setPressed(false);
        if (actions.getViewTreeObserver().isAlive()) actions.getViewTreeObserver().removeOnGlobalLayoutListener(align);
        actions.removeView(search);
        actions.setClipChildren(clipChildren); actions.setClipToPadding(clipPadding);
    }

    boolean attached() { return !closed && search.getParent() == actions; }

    static final class SearchIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;
        SearchIcon(int color, int size) {
            this.size = size; paint.setColor(color); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        }
        void color(int color) {
            if (paint.getColor() != color || paint.getColorFilter() != null) {
                paint.setColor(color); paint.setColorFilter(null); invalidateSelf();
            }
        }
        public int getIntrinsicWidth() { return size; }
        public int getIntrinsicHeight() { return size; }
        public void draw(Canvas canvas) {
            Rect b = getBounds(); float unit = Math.min(b.width(), b.height()) / 24f;
            float left = b.exactCenterX() - 12 * unit, top = b.exactCenterY() - 12 * unit;
            paint.setStrokeWidth(2 * unit);
            canvas.drawCircle(left + 10.5f * unit, top + 10.5f * unit, 6.5f * unit, paint);
            canvas.drawLine(left + 15.2f * unit, top + 15.2f * unit, left + 20 * unit, top + 20 * unit, paint);
        }
        public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
