package dev.jason.gboardpatches.extension.clipboard;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Visual positions only. Every native mutation continues to receive a native model position. */
public final class ClipboardPinned1803Runtime {
    private static final Map<Object, Projection> projections = new WeakHashMap<>();
    private static final Map<Object, Boolean> enabledAdapters = new WeakHashMap<>();
    private static final Map<Object, BoundHolder> holders = new WeakHashMap<>();
    private static final class BoundHolder {
        final WeakReference<Object> adapter, model;
        BoundHolder(Object adapter, Object model) {
            this.adapter = new WeakReference<>(adapter); this.model = new WeakReference<>(model);
        }
    }
    private static final Map<Object, Boolean> queued = new WeakHashMap<>();
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private ClipboardPinned1803Runtime() { }

    private static final class Projection {
        final Object list;
        final int[] positions;
        Projection(Object list, int[] positions) { this.list = list; this.positions = positions; }
    }

    private static Projection projection(Object adapter) throws Exception {
        List<?> items = (List<?>) ClipboardNative1803.field(adapter, "n");
        Projection cached = projections.get(adapter);
        if (cached != null && cached.list == items && cached.positions.length == items.size()) return cached;
        Class<?> model = Class.forName("fih", false, adapter.getClass().getClassLoader());
        java.lang.reflect.Field a = model.getDeclaredField("a"), b = model.getDeclaredField("b"), c = model.getDeclaredField("c");
        a.setAccessible(true); b.setAccessible(true); c.setAccessible(true);
        Projection result = new Projection(items, ClipboardPinnedOrder.indices(items, a.get(null), b.get(null), c.get(null)));
        projections.put(adapter, result);
        return result;
    }

    public static int nativePosition(Object adapter, int visual) {
        if (Looper.myLooper() != Looper.getMainLooper() || visual < 0) return visual;
        try {
            int[] positions = projection(adapter).positions;
            enabledAdapters.put(adapter, Boolean.TRUE);
            return visual < positions.length ? positions[visual] : visual;
        } catch (Throwable ignored) { return visual; }
    }

    /** Called before native bind and before the existing clipboard decoration hooks. */
    public static int bindPosition(Object adapter, Object holder, int visual) {
        int canonical = nativePosition(adapter, visual);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                List<?> items = (List<?>) ClipboardNative1803.field(adapter, "n");
                if (canonical >= 0 && canonical < items.size()) holders.put(holder, new BoundHolder(adapter, items.get(canonical)));
            } catch (Throwable ignored) { }
        }
        return canonical;
    }

    /** Replaces only reviewed clipboard callers of kr.b(), never the framework holder method. */
    public static int holderPosition(Object holder) {
        try {
            int visual = ((Number) ClipboardNative1803.call(holder, "b")).intValue();
            if (visual < 0) return visual;
            BoundHolder bound = holders.get(holder);
            Object adapter = bound == null ? null : bound.adapter.get();
            if (adapter == null) return visual;
            List<?> items = (List<?>) ClipboardNative1803.field(adapter, "n");
            Object model = bound.model.get();
            int canonical = items.indexOf(model);
            if (canonical >= 0) return canonical;
            // A pin/state update may replace the model object with the same persistent ID.
            if (model != null) {
                Object id = ClipboardNative1803.field(model, "d");
                for (int i = 0; i < items.size(); i++) {
                    if (id.equals(ClipboardNative1803.field(items.get(i), "d"))) return i;
                }
            }
            return -1;
        } catch (Throwable ignored) { return -1; }
    }

    public static int visualPosition(Object adapter, int canonical) {
        if (canonical < 0) return canonical;
        try {
            int[] positions = projection(adapter).positions;
            for (int i = 0; i < positions.length; i++) if (positions[i] == canonical) return i;
        } catch (Throwable ignored) { }
        return canonical;
    }

    public static int keyboardVisualPosition(Object keyboard, int canonical) {
        try { return visualPosition(ClipboardNative1803.field(keyboard, "c"), canonical); }
        catch (Throwable ignored) { return canonical; }
    }

    public static void scroll(Object recycler, int canonical) {
        int visual = canonical;
        for (Object adapter : enabledAdapters.keySet()) {
            try {
                if (ClipboardNative1803.field(adapter, "j") == recycler) {
                    visual = visualPosition(adapter, canonical); break;
                }
            } catch (Throwable ignored) { }
        }
        try { invoke(recycler, "ak", new Class<?>[]{int.class}, new Object[]{visual}); }
        catch (Throwable ignored) { }
    }

    public static void changed(Object adapter) { projections.remove(adapter); }

    public static void afterTrim(Object adapter) {
        if (enabledAdapters.containsKey(adapter)) notifyChanged(adapter, 0);
        else changed(adapter);
    }

    /** Native granular notifications name canonical positions. Coalesce into one visual rebind.
     * No animations can replay an obsolete canonical move into a different displayed section. */
    private static void notify(Object adapter, String nativeMethod, Class<?>[] types, Object... args) {
        if (!"fjk".equals(adapter.getClass().getName()) || !enabledAdapters.containsKey(adapter)) {
            try { invoke(adapter, nativeMethod, types, args); } catch (Throwable ignored) { }
            return;
        }
        changed(adapter);
        if (queued.put(adapter, Boolean.TRUE) != null) return;
        WeakReference<Object> reference = new WeakReference<>(adapter);
        UI.post(() -> {
            Object current = reference.get();
            if (current == null) return;
            queued.remove(current);
            try { ClipboardNative1803.call(current, "gz"); } catch (Throwable ignored) { }
        });
    }

    public static void notifyChanged(Object adapter, int position) { notify(adapter, "gA", new Class<?>[]{int.class}, position); }
    public static void notifyInserted(Object adapter, int position) { notify(adapter, "gC", new Class<?>[]{int.class}, position); }
    public static void notifyRemoved(Object adapter, int position) { notify(adapter, "n", new Class<?>[]{int.class}, position); }
    public static void notifyMoved(Object adapter, int from, int to) { notify(adapter, "gD", new Class<?>[]{int.class, int.class}, from, to); }
    public static void notifyRange(Object adapter, int from, int to) { notify(adapter, "gE", new Class<?>[]{int.class, int.class}, from, to); }

    /** Native sticky labels compare canonical section boundaries with visual scroll positions.
     * Use the displayed section's actual header instead; no model or selection is reordered. */
    public static boolean updateSticky(Object adapter) {
        try {
            Projection p = projection(adapter);
            if (p.positions.length == 0 || p.positions[0] == 0) return false;
            Object recycler = ClipboardNative1803.field(adapter, "j");
            if (recycler == null) return false;
            Object layout = ClipboardNative1803.field(recycler, "l");
            int[] starts = (int[]) ClipboardNative1803.call(layout, "R");
            int first = Integer.MAX_VALUE;
            for (int index : starts) if (index >= 0) first = Math.min(first, index);
            if (first >= p.positions.length) return false;
            List<?> items = (List<?>) p.list;
            Object model = items.get(p.positions[first]);
            Class<?> clip = Class.forName("fih", false, adapter.getClass().getClassLoader());
            java.lang.reflect.Field a = clip.getDeclaredField("a"), b = clip.getDeclaredField("b"), c = clip.getDeclaredField("c");
            a.setAccessible(true); b.setAccessible(true); c.setAccessible(true);
            int pinned = items.indexOf(b.get(null)), special = items.indexOf(c.get(null));
            int nativeFirst = p.positions[first];
            String section = nativeFirst < pinned ? "B" : nativeFirst < special ? "C" : "D";
            Object selected = null;
            for (String name : new String[]{"B", "C", "D"}) {
                Object holder = ClipboardNative1803.field(adapter, name);
                if (name.equals(section)) selected = holder;
                if (holder != null && ClipboardNative1803.field(holder, "t") instanceof View view) {
                    view.setVisibility(View.GONE);
                }
            }
            Object keyboard = ClipboardNative1803.field(adapter, "v");
            Object title = ClipboardNative1803.field(keyboard, "f");
            if (title instanceof TextView text && Boolean.TRUE.equals(ClipboardNative1803.field(keyboard, "h"))) {
                if (selected != null && ClipboardNative1803.field(selected, "s") instanceof TextView label) text.setText(label.getText());
            }
            // A header exactly at the viewport top already labels its own section.
            if (selected != null && model != a.get(null) && model != b.get(null) && model != c.get(null)
                    && Boolean.TRUE.equals(ClipboardNative1803.field(adapter, "t"))
                    && ClipboardNative1803.field(selected, "t") instanceof View view) view.setVisibility(View.VISIBLE);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    private static void invoke(Object receiver, String name, Class<?>[] types, Object[] args) throws Exception {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            try { Method method = type.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(receiver, args); return; }
            catch (NoSuchMethodException ignored) { }
        }
    }
}
