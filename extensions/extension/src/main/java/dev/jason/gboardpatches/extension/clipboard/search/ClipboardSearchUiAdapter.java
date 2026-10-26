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

/** A themed vector action next to the native clipboard actions. Native SoftKeyboardView
 * dispatches most touches through its own key graph, bypassing ordinary child onClick;
 * dispatchTouch handles only this button's bounds at the keyboard dispatch boundary. */
public final class ClipboardSearchUiAdapter implements AutoCloseable {
    final ImageButton search;
    private final ViewGroup actions;
    private final boolean clipChildren, clipPadding;
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener align = this::alignToPeers;
    private boolean closed, captured, cancelled;
    private int pointer = -1;

    public ClipboardSearchUiAdapter(ViewGroup actions, Runnable onSearch) {
        this.actions = actions;
        Context context = actions.getContext();
        search = new ImageButton(context);
        TypedArray colors = context.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        int color;
        try { color = colors.getColor(0, 0xffdadce0); } finally { colors.recycle(); }
        search.setImageDrawable(new SearchIcon(color, Math.round(24 * context.getResources().getDisplayMetrics().density)));
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
        Drawable background = peer.getBackground();
        if (background != null && background.getConstantState() != null && search.getTag() != background) {
            search.setBackground(background.getConstantState().newDrawable().mutate());
            search.setTag(background);
            search.setPadding(0, 0, 0, 0);
        }
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

    private boolean contains(MotionEvent event) {
        if (!attached() || !search.isShown() || !search.isEnabled() || !search.isAttachedToWindow()
                || search.getWidth() <= 0 || search.getHeight() <= 0) return false;
        Rect visible = new Rect();
        if (!search.getGlobalVisibleRect(visible)) return false;
        int[] root = new int[2]; search.getRootView().getLocationOnScreen(root);
        visible.offset(root[0], root[1]);
        return visible.contains((int) event.getRawX(), (int) event.getRawY());
    }

    /** Consumes only a gesture started on search. Stock keys and drags are otherwise untouched. */
    boolean dispatchTouch(View keyboard, MotionEvent event) {
        if (closed || keyboard == null || event == null || keyboard.getRootView() != search.getRootView()) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            captured = event.getPointerCount() == 1 && contains(event);
            cancelled = false;
            pointer = captured ? event.getPointerId(0) : -1;
            if (captured) search.setPressed(true);
            return captured;
        }
        if (!captured) return false;
        if (action == MotionEvent.ACTION_POINTER_DOWN || event.findPointerIndex(pointer) < 0) cancelled = true;
        if (action == MotionEvent.ACTION_MOVE && !contains(event)) cancelled = true;
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            boolean click = action == MotionEvent.ACTION_UP && !cancelled && contains(event);
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
