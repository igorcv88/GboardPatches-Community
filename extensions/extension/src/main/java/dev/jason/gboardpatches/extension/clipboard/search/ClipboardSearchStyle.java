package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Appearance of the search surface, read from the native clipboard while it is on screen:
 * backdrop, history cards, their text and the header controls. Every value is what the
 * active theme renders right now; a value that cannot be read falls back to the IME theme.
 */
final class ClipboardSearchStyle {
    final int panelColor, cardColor, textColor, iconColor, controlColor;
    final float cardRadius, textSize;
    final int cardPaddingH, cardPaddingV, cardGap, controlSize, maxLines;
    final Typeface typeface;

    private ClipboardSearchStyle(Builder b) {
        panelColor = b.panelColor; cardColor = b.cardColor; textColor = b.textColor;
        iconColor = b.iconColor; controlColor = b.controlColor; cardRadius = b.cardRadius;
        textSize = b.textSize; cardPaddingH = b.cardPaddingH; cardPaddingV = b.cardPaddingV;
        cardGap = b.cardGap; controlSize = b.controlSize; maxLines = b.maxLines; typeface = b.typeface;
    }

    static ClipboardSearchStyle fallback(Context context) { return new Builder(context).build(); }

    /** Reads the live clipboard: recycler is the native history list, ui the header search action. */
    static ClipboardSearchStyle capture(View recycler, ClipboardSearchUiAdapter ui) {
        Builder b = new Builder(recycler.getContext());
        try { backdrop(b, recycler); } catch (Throwable ignored) { }
        try { card(b, recycler); } catch (Throwable ignored) { }
        try {
            if (ui != null && ui.attached() && ui.controlSize() > 0) {
                b.iconColor = ui.iconColor();
                if (Color.alpha(ui.controlColor()) > 0) b.controlColor = ui.controlColor();
                b.controlSize = ui.controlSize();
            }
        } catch (Throwable ignored) { }
        if (b.controlColor == 0) b.controlColor = ClipboardNativeSkin.withAlpha(b.iconColor, 0x1c);
        return b.build();
    }

    /** Composites the backgrounds between the IME input area and the list at its center. */
    private static void backdrop(Builder b, View recycler) {
        List<View> chain = new ArrayList<>();
        for (View view = recycler; view != null; ) {
            chain.add(0, view);
            // Backgrounds above inputArea are shared with the search slot: never paint them twice.
            if (view.getId() == android.R.id.inputArea || !(view.getParent() instanceof View parent)
                    || parent.getParent() == null || !(parent.getParent() instanceof View)) break;
            view = parent;
        }
        int[] location = new int[2];
        recycler.getLocationInWindow(location);
        int x = location[0] + recycler.getWidth() / 2, y = location[1] + recycler.getHeight() / 2;
        Bitmap pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(pixel);
            View top = chain.get(0);
            for (View view : chain) {
                Drawable background = view.getBackground();
                if (background == null || view.getVisibility() != View.VISIBLE) continue;
                view.getLocationInWindow(location);
                Rect bounds = background.copyBounds();
                if (bounds.isEmpty()) background.setBounds(0, 0, view.getWidth(), view.getHeight());
                int layer = canvas.saveLayerAlpha(0, 0, 1, 1, Math.round(255 * ClipboardNativeSkin.alpha(view, top)));
                canvas.translate(location[0] - x, location[1] - y);
                background.draw(canvas);
                canvas.restoreToCount(layer);
                if (bounds.isEmpty()) background.setBounds(bounds);
            }
            int color = pixel.getPixel(0, 0);
            if (Color.alpha(color) > 0) b.panelColor = color;
        } finally { pixel.recycle(); }
    }

    /** First history card on screen: its rendered color, corner radius, text and spacing. */
    private static void card(Builder b, View recycler) {
        if (!(recycler instanceof ViewGroup list)) return;
        float density = recycler.getResources().getDisplayMetrics().density;
        for (int i = 0; i < list.getChildCount(); i++) {
            View item = list.getChildAt(i);
            if (item.getVisibility() != View.VISIBLE || item.getWidth() <= 0 || item.getHeight() <= 0) continue;
            View card = painted(item);
            TextView text = text(item);
            if (card == null || text == null) continue; // Section headers have no card.
            Bitmap bitmap = ClipboardNativeSkin.render(card.getBackground(), card.getWidth(), card.getHeight());
            if (bitmap == null) continue;
            try {
                Rect area = ClipboardNativeSkin.opaqueBounds(bitmap);
                b.cardColor = ClipboardNativeSkin.centerColor(bitmap, area, ClipboardNativeSkin.alpha(card, item));
                // A rounded corner of radius r enters the diagonal at r(1 - 1/sqrt 2).
                int max = Math.min(area.width(), area.height()) / 2, t = 0;
                while (t < max && Color.alpha(bitmap.getPixel(area.left + t, area.top + t)) < 0x80) t++;
                b.cardRadius = Math.min(max, t / 0.29289f);
            } finally { bitmap.recycle(); }
            Rect cardRect = rectIn(item, card), textRect = rectIn(item, text);
            b.cardPaddingH = clamp(textRect.left - cardRect.left, density, 4, 24);
            b.cardPaddingV = clamp(textRect.top - cardRect.top, density, 4, 24);
            int gap = cardRect.top + (item.getHeight() - cardRect.bottom);
            if (item.getLayoutParams() instanceof ViewGroup.MarginLayoutParams margins) {
                gap += margins.topMargin + margins.bottomMargin;
            }
            b.cardGap = clamp(gap, density, 2, 16);
            b.textColor = text.getCurrentTextColor();
            b.textSize = text.getTextSize();
            b.typeface = text.getTypeface();
            if (text.getMaxLines() > 0 && text.getMaxLines() < 6) b.maxLines = text.getMaxLines();
            return;
        }
    }

    private static View painted(View view) {
        Drawable background = view.getBackground();
        if (background != null && view.getWidth() > 0 && view.getHeight() > 0) {
            Bitmap probe = ClipboardNativeSkin.render(background, view.getWidth(), view.getHeight());
            if (probe != null) { probe.recycle(); return view; }
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                View found = painted(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView text(View view) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof TextView text && text.getText().length() > 0) return text;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = text(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Rect rectIn(View root, View child) {
        Rect rect = new Rect(0, 0, child.getWidth(), child.getHeight());
        if (child != root && root instanceof ViewGroup group) group.offsetDescendantRectToMyCoords(child, rect);
        return rect;
    }

    private static int clamp(int px, float density, int minDp, int maxDp) {
        return Math.max(Math.round(minDp * density), Math.min(Math.round(maxDp * density), px));
    }

    private static final class Builder {
        int panelColor, cardColor, textColor, iconColor, controlColor;
        float cardRadius, textSize;
        int cardPaddingH, cardPaddingV, cardGap, controlSize, maxLines = 2;
        Typeface typeface;

        /** IME theme values, used only where the native clipboard could not be read. */
        Builder(Context context) {
            float density = context.getResources().getDisplayMetrics().density;
            TypedArray values = context.obtainStyledAttributes(new int[]{
                    android.R.attr.colorBackground, android.R.attr.textColorPrimary});
            try {
                panelColor = values.getColor(0, Color.WHITE);
                textColor = values.getColor(1, Color.DKGRAY);
            } finally { values.recycle(); }
            iconColor = textColor;
            cardColor = ClipboardNativeSkin.withAlpha(textColor, 0x14);
            cardRadius = 12 * density;
            textSize = 15 * context.getResources().getDisplayMetrics().scaledDensity;
            cardPaddingH = Math.round(12 * density); cardPaddingV = Math.round(8 * density);
            cardGap = Math.round(4 * density); controlSize = Math.round(40 * density);
        }

        ClipboardSearchStyle build() { return new ClipboardSearchStyle(this); }
    }
}
