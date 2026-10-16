package dev.jason.gboardpatches.extension.clipboard;

import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Reviewed 18.0.3 presentation bridge. Native model order, IDs and callbacks never change. */
public final class ClipboardSearch1803Runtime {
    private static Session active;
    private ClipboardSearch1803Runtime() {}

    public static void opened(Object keyboard) {
        if (!main()) return;
        closeActive();
        try {
            Object adapter = field(keyboard, "c");
            Object listView = field(keyboard, "l");
            if (adapter == null || !(listView instanceof View recycler)) return;
            View root = recycler.getRootView();
            View title = root.findViewById(0x7f0b01c9);
            View actions = root.findViewById(0x7f0b0527);
            View label = root.findViewById(0x7f0b01c8);
            if (!(title instanceof ViewGroup titleGroup) || !(actions instanceof ViewGroup actionGroup)
                    || label == null || !(recycler.getParent() instanceof ViewGroup)) return;
            active = new Session(keyboard, adapter, recycler, titleGroup, label, actionGroup);
            active.refresh();
        } catch (Throwable failure) { report("attach", failure); closeActive(); }
    }
    public static void closed(Object keyboard) {
        if (main() && active != null && active.keyboard == keyboard) closeActive();
    }
    public static void changed(Object adapter) {
        Session session = active;
        if (main() && session != null && session.adapter == adapter) session.scheduleRefresh();
    }
    public static void beforeBind(Object adapter, Object holder, int position) {
        Session session = active;
        if (!main() || session == null || session.adapter != adapter) return;
        try { session.restore((View) field(holder, "a")); }
        catch (Throwable failure) { report("restore card", failure); }
    }
    public static void afterBind(Object adapter, Object holder, int position) {
        Session session = active;
        if (!main() || session == null || session.adapter != adapter || session.query.isEmpty()) return;
        try {
            List<?> items = (List<?>) field(adapter, "n");
            if (position < 0 || position >= items.size()) return;
            Object model = items.get(position);
            View view = (View) field(holder, "a");
            if (!session.visible.contains(model)) session.hide(view);
        } catch (Throwable failure) { report("filter card", failure); }
    }
    private static void closeActive() {
        Session session = active; active = null;
        if (session != null) session.close();
    }
    private static boolean main() { return Looper.myLooper() == Looper.getMainLooper(); }
    private static void report(String operation, Throwable failure) {
        android.util.Log.w("GboardPatches", "Clipboard search: " + operation + " (" + failure.getClass().getSimpleName() + ")");
    }
    private static Object field(Object receiver, String name) throws Exception {
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

    private static final class SavedView {
        final int height, visibility, top, bottom;
        SavedView(View view) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            height = params.height; visibility = view.getVisibility();
            top = params instanceof ViewGroup.MarginLayoutParams m ? m.topMargin : 0;
            bottom = params instanceof ViewGroup.MarginLayoutParams m ? m.bottomMargin : 0;
        }
        void restore(View view) {
            ViewGroup.LayoutParams params = view.getLayoutParams(); params.height = height;
            if (params instanceof ViewGroup.MarginLayoutParams m) { m.topMargin = top; m.bottomMargin = bottom; }
            view.setLayoutParams(params); view.setVisibility(visibility);
        }
    }
    private static final class Session implements AutoCloseable {
        final Object keyboard, adapter;
        final View recycler;
        final ClipboardSearchController worker = ClipboardSearchController.forImeMainThread();
        final ClipboardSearchUiAdapter ui;
        final Map<View, SavedView> hidden = new WeakHashMap<>();
        final Set<Object> visible = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ClipboardSearchPresentation.Row<Object>> rows = List.of();
        String query = "";
        boolean closed, refreshPending;
        Object grid;
        int originalColumns;
        final Runnable refreshTask = () -> { refreshPending = false; if (!closed) refresh(); };
        Session(Object keyboard, Object adapter, View recycler, ViewGroup title, View label, ViewGroup actions) {
            this.keyboard = keyboard; this.adapter = adapter; this.recycler = recycler;
            ui = new ClipboardSearchUiAdapter(title, label, actions, recycler, this::search);
        }
        void scheduleRefresh() {
            if (!closed && !refreshPending) { refreshPending = true; recycler.post(refreshTask); }
        }
        void refresh() {
            try {
                List<?> items = (List<?>) field(adapter, "n");
                List<ClipboardSearchController.Projection> projections = new ArrayList<>();
                List<ClipboardSearchPresentation.Row<Object>> fresh = new ArrayList<>();
                Class<?> clip = adapter.getClass().getClassLoader().loadClass("fih");
                Field recentField = clip.getDeclaredField("a"), pinnedField = clip.getDeclaredField("b"), specialField = clip.getDeclaredField("c");
                recentField.setAccessible(true); pinnedField.setAccessible(true); specialField.setAccessible(true);
                Object recent = recentField.get(null), pinned = pinnedField.get(null), special = specialField.get(null);
                ClipboardSearchController.Section section = ClipboardSearchController.Section.RECENT;
                for (Object model : items) {
                    if (model == null) continue;
                    boolean header = model == recent || model == pinned || model == special;
                    if (model == pinned) section = ClipboardSearchController.Section.PINNED;
                    if (model == special) section = ClipboardSearchController.Section.SPECIAL;
                    long id = ((Number) field(model, "d")).longValue();
                    fresh.add(new ClipboardSearchPresentation.Row<>(id, section, model, header));
                    if (!header) {
                        boolean sensitive = Boolean.TRUE.equals(call(model, "m"));
                        String text = sensitive ? null : (String) call(model, "i");
                        projections.add(new ClipboardSearchController.Projection(id, section, text, sensitive));
                    }
                }
                rows = List.copyOf(fresh);
                worker.replaceSnapshot(projections);
                search(query);
            } catch (Throwable failure) { report("snapshot", failure); ui.reset(); }
        }
        void search(String value) {
            if (closed) return;
            query = value == null ? "" : value;
            if (query.isEmpty()) {
                worker.search("", ignored -> {}); // Invalidates every older in-flight result.
                visible.clear(); restoreAll(); ui.showEmpty(false); notifyCards(); return;
            }
            List<ClipboardSearchPresentation.Row<Object>> snapshot = rows;
            worker.search(query, ids -> {
                if (closed || rows != snapshot) return;
                ClipboardSearchPresentation.Result<Object> result = ClipboardSearchPresentation.filter(snapshot, ids);
                visible.clear(); visible.addAll(result.items);
                ui.showEmpty(result.items.isEmpty()); notifyCards();
            });
        }
        void notifyCards() {
            recycler.post(() -> {
                if (closed) return;
                try { updateColumns(); call(adapter, "gz"); recycler.requestLayout(); }
                catch (Throwable failure) { report("refresh cards", failure); }
            });
        }
        void updateColumns() throws Exception {
            if (grid == null) {
                Object manager = field(recycler, "l");
                if (!manager.getClass().getName().equals("android.support.v7.widget.StaggeredGridLayoutManager")) return;
                grid = manager; originalColumns = ((Number) field(grid, "a")).intValue();
            }
            Method spans = grid.getClass().getDeclaredMethod("M", int.class);
            spans.setAccessible(true); spans.invoke(grid, query.isEmpty() ? originalColumns : 1);
        }
        void hide(View view) {
            if (view == null || view.getLayoutParams() == null) return;
            hidden.computeIfAbsent(view, SavedView::new);
            ViewGroup.LayoutParams params = view.getLayoutParams(); params.height = 0;
            if (params instanceof ViewGroup.MarginLayoutParams m) { m.topMargin = 0; m.bottomMargin = 0; }
            view.setLayoutParams(params); view.setVisibility(View.GONE);
        }
        void restore(View view) { SavedView saved = hidden.remove(view); if (saved != null) saved.restore(view); }
        void restoreAll() {
            for (Map.Entry<View, SavedView> entry : hidden.entrySet()) entry.getValue().restore(entry.getKey());
            hidden.clear();
        }
        public void close() {
            if (closed) return;
            closed = true; recycler.removeCallbacks(refreshTask); worker.close();
            restoreAll(); rows = List.of(); visible.clear(); query = "";
            if (grid != null) {
                try { updateColumns(); } catch (Throwable failure) { report("restore columns", failure); }
            }
            ui.close();
        }
    }
}
