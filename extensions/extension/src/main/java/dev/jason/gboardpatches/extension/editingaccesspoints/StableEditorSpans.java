package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.text.Annotation;
import android.os.Build;
import android.text.NoCopySpan;
import android.text.Spanned;
import android.text.style.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Value descriptions of supported spans, independent of reparceled object identity. */
final class StableEditorSpans {
    /** Null rejects local history for an unknown style instead of ignoring its semantics. */
    static Map<List<Object>, Integer> describe(CharSequence text) {
        Map<List<Object>, Integer> values = new HashMap<>();
        if (!(text instanceof Spanned spanned)) return Map.of();
        for (Object span : spanned.getSpans(0, text.length(), Object.class)) {
            if ((spanned.getSpanFlags(span) & Spanned.SPAN_COMPOSING) != 0) return null;
            if (span instanceof NoCopySpan) continue; // Selection/editor bookkeeping, not content.
            Object value = value(span);
            if (value == null) return null;
            List<Object> key = List.of(span.getClass().getName(), spanned.getSpanStart(span),
                    spanned.getSpanEnd(span), spanned.getSpanFlags(span), value);
            values.merge(key, 1, Integer::sum);
        }
        return Map.copyOf(values);
    }

    private static Object value(Object span) {
        Class<?> type = span.getClass();
        if (type == StyleSpan.class) {
            StyleSpan style = (StyleSpan) span;
            // Font-weight adjustment was added in API 33. The same patch also runs on
            // API 26–32, where resolving that method would discard otherwise valid history.
            return Build.VERSION.SDK_INT >= 33
                    ? List.of(style.getStyle(), style.getFontWeightAdjustment())
                    : List.of(style.getStyle());
        }
        if (type == URLSpan.class) return ((URLSpan) span).getURL();
        if (type == ForegroundColorSpan.class) return ((ForegroundColorSpan) span).getForegroundColor();
        if (type == BackgroundColorSpan.class) return ((BackgroundColorSpan) span).getBackgroundColor();
        if (type == AbsoluteSizeSpan.class) {
            AbsoluteSizeSpan size = (AbsoluteSizeSpan) span;
            return List.of(size.getSize(), size.getDip());
        }
        if (type == RelativeSizeSpan.class) return ((RelativeSizeSpan) span).getSizeChange();
        if (type == ScaleXSpan.class) return ((ScaleXSpan) span).getScaleX();
        if (type == TypefaceSpan.class) return ((TypefaceSpan) span).getFamily();
        if (type == LocaleSpan.class) return ((LocaleSpan) span).getLocales().toLanguageTags();
        if (type == Annotation.class) {
            Annotation annotation = (Annotation) span;
            return List.of(annotation.getKey(), annotation.getValue());
        }
        if (type == UnderlineSpan.class || type == StrikethroughSpan.class
                || type == SuperscriptSpan.class || type == SubscriptSpan.class) return true;
        return null;
    }
    private StableEditorSpans() {}
}
