package dev.jason.gboardpatches.extension.quicktoolbar;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Owns only a transient foreground/content visibility; the stock button listener stays installed. */
public final class QuickToolbarBinding implements AutoCloseable {
    public interface Favorites {
        boolean show();
        void hide();
    }
    private final View button, content;
    private final Favorites favorites;
    private final Drawable originalForeground;
    private final CharSequence originalDescription;
    private final int originalVisibility;
    private final QuickToolbarState state = new QuickToolbarState();
    private final Arrow arrow;
    private boolean decorated, closed;

    public QuickToolbarBinding(View button, View content, int color, Favorites favorites) {
        this.button = button; this.content = content; this.favorites = favorites;
        originalForeground = button.getForeground();
        originalDescription = button.getContentDescription();
        originalVisibility = content.getVisibility();
        arrow = new Arrow(color);
    }

    public void update(boolean enabled, boolean active, boolean candidates) {
        if (closed) return;
        boolean wasTools = state.mode() == QuickToolbarState.Mode.QUICK_TOOLS;
        state.update(enabled, active, candidates);
        if (wasTools && state.mode() != QuickToolbarState.Mode.QUICK_TOOLS) favorites.hide();
        render();
    }

    /** True consumes the stock all-shortcuts event. Other event types must bypass this method. */
    public boolean onMenuEvent() {
        if (closed || state.mode() == QuickToolbarState.Mode.STOCK_TOOLBAR) return false;
        QuickToolbarState.Click action = state.click();
        if (action == QuickToolbarState.Click.SHOW_FAVORITES) {
            boolean shown = false;
            try { shown = favorites.show(); }
            finally {
                if (!shown) {
                    favorites.hide();
                    state.detach();
                    state.update(true, true, true);
                }
                render();
            }
        } else {
            favorites.hide();
            render();
        }
        return true;
    }

    public QuickToolbarState.Mode mode() { return state.mode(); }

    private void render() {
        if (state.mode() == QuickToolbarState.Mode.STOCK_TOOLBAR) {
            if (decorated) {
                content.setVisibility(originalVisibility);
                if (button.getForeground() == arrow) button.setForeground(originalForeground);
                button.setContentDescription(originalDescription);
                decorated = false;
            }
            return;
        }
        decorated = true;
        content.setVisibility(View.INVISIBLE);
        arrow.back = (state.mode() == QuickToolbarState.Mode.QUICK_TOOLS)
                != (button.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        button.setForeground(arrow);
        button.setContentDescription(GboardSettingsText.get(button.getContext(),
                state.mode() == QuickToolbarState.Mode.QUICK_TOOLS
                        ? R.string.gboard_quick_toolbar_suggestions : R.string.gboard_quick_toolbar_favorites));
        arrow.invalidateSelf();
    }

    @Override public void close() {
        if (closed) return;
        update(false, false, false);
        closed = true;
    }

    private static final class Arrow extends Drawable {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean back;
        Arrow(int color) {
            paint.setColor(color); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override public void draw(Canvas canvas) {
            float unit = Math.min(getBounds().width(), getBounds().height()) / 24f;
            float cx = getBounds().exactCenterX(), cy = getBounds().exactCenterY();
            float dir = back ? -1 : 1;
            paint.setStrokeWidth(2 * unit);
            Path path = new Path();
            path.moveTo(cx - dir * 3 * unit, cy - 5 * unit);
            path.lineTo(cx + dir * 2 * unit, cy);
            path.lineTo(cx - dir * 3 * unit, cy + 5 * unit);
            canvas.drawPath(path, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
