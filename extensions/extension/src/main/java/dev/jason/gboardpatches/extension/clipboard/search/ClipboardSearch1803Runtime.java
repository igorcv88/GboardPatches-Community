package dev.jason.gboardpatches.extension.clipboard;

import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reviewed 18.0.3 bridge from the native clipboard panel to the composite search.
 * Native model order, IDs, views and callbacks are never changed; the panel only gains a
 * search button, which takes an immutable text snapshot of the current clips.
 */
public final class ClipboardSearch1803Runtime {
    private static Session active;
    private ClipboardSearch1803Runtime() {}

    public static void opened(Object keyboard) {
        if (!main()) return;
        closeActive();
        traceBackKey(keyboard);
        if (!ClipboardNativeSearch1803.available()) return; // Fails closed: no button.
        try {
            Object adapter = field(keyboard, "c");
            Object listView = field(keyboard, "l");
            if (adapter == null || !(listView instanceof View recycler)) return;
            View actions = recycler.getRootView().findViewById(0x7f0b0527);
            if (!(actions instanceof ViewGroup actionGroup)) return;
            active = new Session(keyboard, adapter, recycler, actionGroup);
        } catch (Throwable failure) { report("attach", failure); closeActive(); }
    }

    /** ClipboardKeyboard.h(): the panel goes away; an open search keeps its own snapshot. */
    public static void closed(Object keyboard) {
        if (main() && active != null && active.keyboard == keyboard) closeActive();
    }

    /** Lfjk.M(Z): native list refresh. Snapshots are taken on demand, nothing to update. */
    public static void changed(Object adapter) { ClipboardPinned1803Runtime.changed(adapter); }

    private static boolean traced;

    /**
     * Device trace for T1a, once per process and without clip content: the action code of the
     * back-to-alphabet key (0x7f0b0521) as the panel shows it. Read-only.
     */
    private static void traceBackKey(Object keyboard) {
        if (traced) return;
        try {
            Object listView = field(keyboard, "l");
            if (!(listView instanceof View recycler)) return;
            Object action = ClipboardNative1803.backKeyAction(recycler.getRootView());
            Integer code = ClipboardNative1803.actionCode(action);
            traced = true;
            android.util.Log.i("GboardPatches", "Clipboard back-to-prime key: "
                    + (action == null ? "absent" : action.getClass().getName() + " code=" + code));
        } catch (Throwable ignored) { }
    }

    private static void closeActive() {
        Session session = active; active = null;
        if (session != null) session.close();
    }

    /** Text clips in native order; sensitive clips, headers and non-text items are excluded. */
    static List<ClipboardSearchSurface.Clip> snapshot(Object adapter) throws Exception {
        List<?> items = (List<?>) field(adapter, "n");
        Class<?> clip = adapter.getClass().getClassLoader().loadClass("fih");
        Object recent = staticField(clip, "a"), pinned = staticField(clip, "b"), special = staticField(clip, "c");
        ClipboardSearchController.Section section = ClipboardSearchController.Section.RECENT;
        List<ClipboardSearchSurface.Clip> clips = new ArrayList<>();
        for (Object model : items) {
            if (model == null) continue;
            if (model == pinned) { section = ClipboardSearchController.Section.PINNED; continue; }
            if (model == special) { section = ClipboardSearchController.Section.SPECIAL; continue; }
            if (model == recent) continue;
            if (Boolean.TRUE.equals(call(model, "m"))) continue;
            Object text = call(model, "i");
            if (!(text instanceof String value) || value.isEmpty()) continue;
            clips.add(new ClipboardSearchSurface.Clip(((Number) field(model, "d")).longValue(), section, value));
        }
        return clips;
    }

    private static boolean main() { return Looper.myLooper() == Looper.getMainLooper(); }
    private static void report(String operation, Throwable failure) {
        android.util.Log.w("GboardPatches", "Clipboard search: " + operation + " (" + failure.getClass().getSimpleName() + ")");
    }
    private static Object staticField(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    static Object field(Object receiver, String name) throws Exception {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(receiver); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object call(Object receiver, String name) throws Exception {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            try { Method method = type.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(receiver); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }

    private static final class Session implements AutoCloseable {
        final Object keyboard, adapter;
        final View recycler;
        final ClipboardSearchUiAdapter ui;
        Session(Object keyboard, Object adapter, View recycler, ViewGroup actions) {
            this.keyboard = keyboard; this.adapter = adapter; this.recycler = recycler;
            ui = new ClipboardSearchUiAdapter(actions, this::search);
        }
        void search() {
            try { ClipboardNativeSearch1803.open(keyboard, recycler, snapshot(adapter)); }
            catch (Throwable failure) { report("snapshot", failure); }
        }
        public void close() { ui.close(); }
    }
}
