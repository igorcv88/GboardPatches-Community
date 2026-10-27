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
    private int backgroundWidth, backgroundHeight, backgroundState, controlColor, backdropColor;
    private final java.util.Map<Drawable, Boolean> paintedBackgrounds = new java.util.WeakHashMap<>();
    private java.lang.ref.WeakReference<Drawable> renderedGlyph = new java.lang.ref.WeakReference<>(null);
    private int renderedGlyphState;
    private Integer renderedGlyphColor;

    public ClipboardSearchUiAdapter(ViewGroup actions, Runnable onSearch) {
        this.actions = actions;
        Context context = actions.getContext();
        // No imageButtonStyle: its framework tint state would overwrite Gboard's skin
        // on background assignment and pressed-state updates. Appearance comes from Edit.
        search = new ImageButton(context, null, 0);
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
        View edit = actions.findViewById(0x7f0b0526);
        if (edit == null || !edit.isShown()) edit = actions.findViewById(0x7f0b0525);
        View template = edit != null && edit.isShown() ? edit : peer;
        copyNativeTint(template);
        copyGlyphSize(template);
        int width = peer.getWidth(), height = peer.getHeight();
        int right = peer.getLeft(), top = peer.getTop();
        if (search.getLeft() != right - width || search.getTop() != top
                || search.getWidth() != width || search.getHeight() != height) {
            search.layout(right - width, top, right, top + height);
        }
        copyNativeBackground(template);
    }

    /** Color of the disc behind the lens, as the native Edit control renders it. */
    int controlColor() { return controlColor; }
    int iconColor() { return ((SearchIcon) search.getDrawable()).foreground(); }
    int controlSize() { return search.getHeight(); }
    /** Composited header backdrop behind the disc, as last evaluated. */
    int backdropColor() { return backdropColor; }

    /**
     * Previous builds cloned the native drawable via ConstantState and replayed its runtime
     * tint/filter/alpha/foreground. Gboard's gray disc is a white asset made translucent gray
     * at runtime; every property the replay missed (tint evaluated against the search
     * button's state set instead of the key's, runtime paint color, a cloned highlight)
     * produced an opaque white disc, and the white lens vanished on it. The disc is now
     * built from the color the live native background actually renders, in its own state.
     */
    private void copyNativeBackground(View peer) {
        View backgroundView = findBackground(peer);
        Drawable source = backgroundView == null ? null : backgroundView.getBackground();
        int foreground = ((SearchIcon) search.getDrawable()).foreground();
        float alpha = backgroundView == null ? 1f : ClipboardNativeSkin.alpha(backgroundView, peer);
        int state = 31 * (source == null ? 0 : java.util.Arrays.hashCode(backgroundView.getDrawableState())) + foreground;
        if (source != null) state = 31 * (31 * state + source.getAlpha() + Float.floatToIntBits(alpha))
                + backgroundView.getWidth() * 8191 + backgroundView.getHeight();
        if (nativeBackground == source && backgroundWidth == peer.getWidth() && backgroundHeight == peer.getHeight()
                && backgroundState == state && search.getBackground() != null) return;
        nativeBackground = source;
        backgroundWidth = peer.getWidth(); backgroundHeight = peer.getHeight(); backgroundState = state;
        Rect painted = null;
        int color = 0;
        android.graphics.Bitmap bitmap = source == null ? null
                : ClipboardNativeSkin.render(source, backgroundView.getWidth(), backgroundView.getHeight());
        if (bitmap != null) {
            try {
                painted = ClipboardNativeSkin.opaqueBounds(bitmap);
                color = ClipboardNativeSkin.centerColor(bitmap, painted, alpha);
            } finally { bitmap.recycle(); }
            if (backgroundView != peer && peer instanceof ViewGroup group) group.offsetDescendantRectToMyCoords(backgroundView, painted);
        }
        if (painted == null) {
            painted = new Rect(0, 0, peer.getWidth(), peer.getHeight());
            color = ClipboardNativeSkin.withAlpha(foreground, 0x1c);
        }
        // Last guard, on what is actually composited: lens over disc over the header's own
        // backdrop. A disc the lens is not legible on is never accepted.
        int[] at = new int[2];
        peer.getLocationInWindow(at);
        backdropColor = ClipboardNativeSkin.backdrop(actions, at[0] + painted.centerX(), at[1] + painted.centerY(), 0);
        color = ClipboardNativeSkin.legibleDisc(foreground, color, backdropColor);
        controlColor = color;
        int diameter = Math.min(painted.width(), painted.height());
        int left = painted.centerX() - diameter / 2, top = painted.centerY() - diameter / 2;
        backgroundBounds.set(left, top, left + diameter, top + diameter);
        int insetRight = Math.max(0, peer.getWidth() - backgroundBounds.right);
        int insetBottom = Math.max(0, peer.getHeight() - backgroundBounds.bottom);
        search.setBackground(new android.graphics.drawable.InsetDrawable(disc(color),
                backgroundBounds.left, backgroundBounds.top, insetRight, insetBottom));
        // Own press feedback, shaped like the disc: a cloned native highlight could cover the lens.
        android.graphics.drawable.StateListDrawable pressed = new android.graphics.drawable.StateListDrawable();
        pressed.addState(new int[]{android.R.attr.state_pressed}, disc(ClipboardNativeSkin.withAlpha(foreground, 0x29)));
        search.setForeground(new android.graphics.drawable.InsetDrawable(pressed,
                backgroundBounds.left, backgroundBounds.top, insetRight, insetBottom));
        search.setPadding(Math.max(0, backgroundBounds.left), Math.max(0, backgroundBounds.top), insetRight, insetBottom);
    }

    private static Drawable disc(int color) {
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(color);
        return circle;
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
            android.graphics.Bitmap probe = ClipboardNativeSkin.render(background, view.getWidth(), view.getHeight());
            painted = probe != null;
            if (probe != null) probe.recycle();
            paintedBackgrounds.put(background, painted);
        }
        return painted ? view : null;
    }

    /** Lens drawn at the size of the native glyph, so both controls look proportional. */
    private void copyGlyphSize(View template) {
        android.widget.ImageView glyph = glyph(template);
        int size = glyph == null ? 0 : ClipboardNativeSkin.glyphSize(glyph);
        SearchIcon icon = (SearchIcon) search.getDrawable();
        if (size > 0 && icon.size(size)) { search.setImageDrawable(null); search.setImageDrawable(icon); }
    }

    private static android.widget.ImageView glyph(View view) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof android.widget.ImageView image && image.getDrawable() != null) return image;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.ImageView found = glyph(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Rendered once per native glyph drawable and state, not on every layout pass. */
    private Integer glyphColor(android.widget.ImageView image) {
        Drawable glyph = image.getDrawable();
        int state = java.util.Arrays.hashCode(image.getDrawableState()) * 31 + image.getImageAlpha();
        if (glyph != null && glyph == renderedGlyph.get() && state == renderedGlyphState) return renderedGlyphColor;
        renderedGlyph = new java.lang.ref.WeakReference<>(glyph);
        renderedGlyphState = state;
        renderedGlyphColor = ClipboardNativeSkin.glyphColor(image);
        return renderedGlyphColor;
    }

    private boolean copyNativeTint(View peer) {
        if (peer instanceof android.widget.ImageView image) {
            Integer rendered = glyphColor(image);
            if (rendered != null) { ((SearchIcon) search.getDrawable()).color(rendered); return true; }
            android.content.res.ColorStateList tint = image.getImageTintList();
            if (tint != null) {
                search.getDrawable().clearColorFilter();
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
        if (!(source instanceof ViewGroup viewport)) return false;
        View ancestor = search;
        while (ancestor != source) {
            android.view.ViewParent parent = ancestor.getParent();
            if (!(parent instanceof View view)) return false;
            ancestor = view;
        }
        if (!search.getGlobalVisibleRect(new Rect())) return false;
        Rect target = new Rect(0, 0, search.getWidth(), search.getHeight());
        // Stay entirely in the same hierarchy as InputView's local MotionEvent. No raw,
        // screen, window or root-relative coordinate arithmetic is needed.
        viewport.offsetDescendantRectToMyCoords(search, target);
        if (!target.intersect(0, 0, viewport.getWidth(), viewport.getHeight())) return false;
        return target.contains((int) event.getX(), (int) event.getY());
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
        private int size;
        private ColorFilter sampledFilter;
        private int sampledBase, sampledColor;
        SearchIcon(int color, int size) {
            this.size = size; paint.setColor(color); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        }
        boolean size(int value) {
            if (value <= 0 || value == size) return false;
            size = value; invalidateSelf(); return true;
        }
        void color(int color) {
            if (paint.getColor() != color || paint.getColorFilter() != null) {
                paint.setColor(color); paint.setColorFilter(null); invalidateSelf();
            }
        }
        int foreground() {
            ColorFilter filter = paint.getColorFilter();
            if (filter == null) return paint.getColor();
            if (filter == sampledFilter && sampledBase == paint.getColor()) return sampledColor;
            // ColorFilter exposes no public color getter. Sample its rendered result using
            // public graphics APIs; do not depend on hidden PorterDuff implementation fields.
            android.graphics.Bitmap pixel = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
            try {
                Paint fill = new Paint(paint); fill.setStyle(Paint.Style.FILL);
                new Canvas(pixel).drawRect(0, 0, 1, 1, fill);
                int color = pixel.getPixel(0, 0);
                sampledFilter = filter; sampledBase = paint.getColor();
                sampledColor = Color.alpha(color) > 0 ? color : paint.getColor();
                return sampledColor;
            } finally { pixel.recycle(); }
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
