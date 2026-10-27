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

    /** True when foreground is distinguishable on background (a white lens on a white disc is not). */
    static boolean contrasts(int foreground, int background) {
        if (Color.alpha(background) < 0xb0) return true;
        int distance = Math.abs(Color.red(foreground) - Color.red(background))
                + Math.abs(Color.green(foreground) - Color.green(background))
                + Math.abs(Color.blue(foreground) - Color.blue(background));
        return distance >= 96;
    }
}
