package dev.jason.gboardpatches.extension.clipboard;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

/**
 * Reads Gboard's skin from what its live drawables actually render.
 *
 * <p>Cloning a native drawable through ConstantState and replaying tint, filter, alpha and
 * state one property at a time loses whatever the replay misses (state-dependent tint
 * evaluated against the wrong state set, runtime paint colors, container alpha). The white
 * base asset then shows through. Drawing the live drawable in its own state cannot miss any
 * of them. Native drawables are never mutated; temporary bounds are restored.
 */
final class ClipboardNativeSkin {
    private ClipboardNativeSkin() {}

    /** Renders a live drawable at the given size, or null when nothing is painted. */
    static Bitmap render(Drawable drawable, int width, int height) {
        if (drawable == null || width <= 0 || height <= 0) return null;
        Bitmap bitmap = null;
        Rect bounds = drawable.copyBounds();
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            if (bounds.width() != width || bounds.height() != height) drawable.setBounds(0, 0, width, height);
            Canvas canvas = new Canvas(bitmap);
            canvas.translate(-drawable.getBounds().left, -drawable.getBounds().top);
            drawable.draw(canvas);
            if (opaqueBounds(bitmap) != null) return bitmap;
        } catch (Throwable ignored) { }
        finally { if (!bounds.equals(drawable.getBounds())) drawable.setBounds(bounds); }
        if (bitmap != null) bitmap.recycle();
        return null;
    }

    /** One pixel of a live drawable at (x, y) of a width x height rendering; 0 if unavailable. */
    static int pixel(Drawable drawable, int width, int height, int x, int y) {
        if (drawable == null || width <= 0 || height <= 0) return 0;
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        Rect bounds = drawable.copyBounds();
        try {
            if (bounds.width() != width || bounds.height() != height) drawable.setBounds(0, 0, width, height);
            Canvas canvas = new Canvas(bitmap);
            canvas.translate(-drawable.getBounds().left - x, -drawable.getBounds().top - y);
            drawable.draw(canvas);
            return bitmap.getPixel(0, 0);
        } catch (Throwable ignored) {
            return 0;
        } finally {
            if (!bounds.equals(drawable.getBounds())) drawable.setBounds(bounds);
            bitmap.recycle();
        }
    }

    /** Smallest rectangle containing painted pixels, or null. */
    static Rect opaqueBounds(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (Color.alpha(pixels[y * width + x]) == 0) continue;
            left = Math.min(left, x); right = Math.max(right, x);
            top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        return right < 0 ? null : new Rect(left, top, right + 1, bottom + 1);
    }

    /** Color of the painted center, scaled by the alpha of the views that host it. */
    static int centerColor(Bitmap bitmap, Rect painted, float alpha) {
        int color = bitmap.getPixel(painted.centerX(), painted.centerY());
        return withAlpha(color, Math.round(Color.alpha(color) * alpha));
    }

    /** Most opaque pixel of the native glyph: icon tint, filter and vector tint included. */
    static Integer glyphColor(ImageView image) {
        Drawable glyph = image.getDrawable();
        if (glyph == null) return null;
        Rect bounds = glyph.getBounds();
        int width = bounds.width() > 0 ? bounds.width() : glyph.getIntrinsicWidth();
        int height = bounds.height() > 0 ? bounds.height() : glyph.getIntrinsicHeight();
        Bitmap bitmap = render(glyph, width, height);
        if (bitmap == null) return null;
        try {
            int best = 0;
            int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
            for (int pixel : pixels) if (Color.alpha(pixel) > Color.alpha(best)) best = pixel;
            return Color.alpha(best) == 0 ? null : best;
        } finally { bitmap.recycle(); }
    }

    /** Size at which the native glyph is drawn on screen, or 0 when unknown. */
    static int glyphSize(ImageView image) {
        Drawable glyph = image.getDrawable();
        if (glyph == null) return 0;
        android.graphics.RectF rect = new android.graphics.RectF(glyph.getBounds());
        if (rect.isEmpty()) return 0;
        image.getImageMatrix().mapRect(rect);
        return Math.round(Math.max(rect.width(), rect.height()));
    }

    /** Product of the alpha of view and its ancestors up to and including stop. */
    static float alpha(View view, View stop) {
        float alpha = 1f;
        for (View current = view; current != null; ) {
            alpha *= current.getAlpha();
            if (current == stop || !(current.getParent() instanceof View parent)) break;
            current = parent;
        }
        return alpha;
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    /** WCAG 2.x non-text contrast minimum (SC 1.4.11) for the lens against its disc. */
    static final double MIN_CONTRAST = 3.0;

    /** Source-over compositing of straight (non-premultiplied) ARGB colors. */
    static int over(int top, int bottom) {
        float ta = Color.alpha(top) / 255f, ba = Color.alpha(bottom) / 255f;
        float a = ta + ba * (1 - ta);
        if (a <= 0f) return 0;
        int r = Math.round((Color.red(top) * ta + Color.red(bottom) * ba * (1 - ta)) / a);
        int g = Math.round((Color.green(top) * ta + Color.green(bottom) * ba * (1 - ta)) / a);
        int b = Math.round((Color.blue(top) * ta + Color.blue(bottom) * ba * (1 - ta)) / a);
        return Color.argb(Math.round(a * 255), r, g, b);
    }

    static double luminance(int color) {
        double r = channel(Color.red(color)), g = channel(Color.green(color)), b = channel(Color.blue(color));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /**
     * Contrast of the lens against the disc as composited on screen: lens over disc over the
     * real backdrop. Whatever the backdrop leaves translucent is unknown (blurred or plain app
     * content behind the IME window), so both extremes are evaluated and the worse one counts.
     */
    static double renderedContrast(int lens, int disc, int backdrop) {
        double worst = Double.MAX_VALUE;
        for (int behind : new int[]{Color.BLACK, Color.WHITE}) {
            int surface = over(disc, over(backdrop, behind));
            worst = Math.min(worst, contrast(over(lens, surface), surface));
        }
        return worst;
    }

    /**
     * The native disc when the lens is legible on it in place; otherwise the native-looking
     * translucent disc of the lens color; otherwise the least opaque neutral disc (dark under a
     * light lens, light under a dark one) on which it is.
     */
    static int legibleDisc(int lens, int disc, int backdrop) {
        if (Color.alpha(disc) > 0 && renderedContrast(lens, disc, backdrop) >= MIN_CONTRAST) return disc;
        int tinted = withAlpha(lens, 0x1c);
        if (renderedContrast(lens, tinted, backdrop) >= MIN_CONTRAST) return tinted;
        int tone = luminance(lens) > 0.18 ? Color.BLACK : Color.WHITE;
        for (int alpha = 0x1c; alpha < 0xff; alpha += 0x0c) {
            int candidate = withAlpha(tone, alpha);
            if (renderedContrast(lens, candidate, backdrop) >= MIN_CONTRAST) return candidate;
        }
        return withAlpha(tone, 0xff);
    }

    /**
     * Color the backgrounds of view and its ancestors composite to at window point (x, y).
     * Stops after an ancestor with id stopId (inclusive) or at the window root, which is
     * included only when stopId is 0. Native drawables are not mutated (bounds restored).
     */
    static int backdrop(View view, int x, int y, int stopId) {
        java.util.List<View> chain = new java.util.ArrayList<>();
        for (View current = view; current != null; ) {
            boolean root = !(current.getParent() instanceof View);
            if (root && stopId != 0) break;
            chain.add(0, current);
            if (root || (stopId != 0 && current.getId() == stopId)) break;
            current = (View) current.getParent();
        }
        if (chain.isEmpty()) return 0;
        Bitmap pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(pixel);
            int[] location = new int[2];
            View top = chain.get(0);
            for (View current : chain) {
                Drawable background = current.getBackground();
                if (background == null || current.getVisibility() != View.VISIBLE) continue;
                current.getLocationInWindow(location);
                Rect bounds = background.copyBounds();
                if (bounds.isEmpty()) background.setBounds(0, 0, current.getWidth(), current.getHeight());
                int layer = canvas.saveLayerAlpha(0, 0, 1, 1, Math.round(255 * alpha(current, top)));
                canvas.translate(location[0] - x, location[1] - y);
                background.draw(canvas);
                canvas.restoreToCount(layer);
                if (bounds.isEmpty()) background.setBounds(bounds);
            }
            return pixel.getPixel(0, 0);
        } catch (Throwable ignored) {
            return 0;
        } finally { pixel.recycle(); }
    }
}
