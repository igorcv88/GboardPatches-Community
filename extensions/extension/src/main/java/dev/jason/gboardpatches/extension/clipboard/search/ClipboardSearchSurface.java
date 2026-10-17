package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Results surface shown above the native alphabet keyboard while searching.
 * It holds an immutable text snapshot of the clipboard (sensitive clips are never included),
 * filters it with {@link ClipboardSearchController} off the IME thread and reports the picked
 * clip. It never touches an InputConnection itself.
 */
public final class ClipboardSearchSurface extends LinearLayout {
    public static final class Clip {
        public final long id;
        public final ClipboardSearchController.Section section;
        public final String text;
        public Clip(long id, ClipboardSearchController.Section section, String text) {
            this.id = id; this.section = section; this.text = text;
        }
    }

    public interface Listener {
        void onPick(Clip clip);
        void onClose();
        /** Explicit, confirmed delete request for one clip. */
        default void onDelete(Clip clip) { }
    }

    final View field;
    final ImageButton close;
    final ListView results;
    final TextView empty;
    private final ClipboardSearchController worker;
    private final Listener listener;
    private final Adapter adapter = new Adapter();
    private final TextWatcher watcher;
    private List<Clip> clips = List.of();
    private Map<Long, Clip> byId = Map.of();
    // Query for which adapter.items was last confirmed by the search worker.
    // A snapshot refresh must not promote unfiltered rows into an active query.
    private String displayedQuery = "";
    private boolean closed, deleteEnabled;
    private long pendingDelete = Long.MIN_VALUE;

    public ClipboardSearchSurface(Context context, View field, int heightPx,
            ClipboardSearchController worker, Listener listener) {
        super(context);
        this.field = field; this.worker = worker; this.listener = listener;
        setOrientation(VERTICAL);
        int foreground = themeColor(context, android.R.attr.textColorPrimary, Color.DKGRAY);
        setBackgroundColor(themeColor(context, android.R.attr.colorBackground, Color.WHITE));
        setLayoutParams(new ViewGroup.LayoutParams(-1, heightPx));

        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout slot = new FrameLayout(context);
        slot.addView(field, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        bar.addView(slot, new LayoutParams(0, dp(context, 44), 1));
        close = new ImageButton(context);
        close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setContentDescription("Close clipboard search");
        close.setOnClickListener(v -> { if (!this.closed) this.listener.onClose(); });
        bar.addView(close, new LayoutParams(dp(context, 44), dp(context, 44)));
        addView(bar, new LayoutParams(-1, -2));

        results = new ListView(context);
        results.setAdapter(adapter);
        results.setOnItemClickListener((parent, view, position, id) -> {
            if (this.closed) return;
            Clip clip = adapter.getItem(position);
            if (pendingDelete != Long.MIN_VALUE) {
                // A second, explicit tap on the marked row confirms; any other tap only cancels.
                boolean confirm = pendingDelete == clip.id;
                setPendingDelete(Long.MIN_VALUE);
                if (confirm) this.listener.onDelete(clip);
                return;
            }
            this.listener.onPick(clip);
        });
        results.setOnItemLongClickListener((parent, view, position, id) -> {
            if (this.closed || !deleteEnabled) return false;
            setPendingDelete(adapter.getItem(position).id);
            return true;
        });
        addView(results, new LayoutParams(-1, 0, 1));
        empty = new TextView(context);
        empty.setText("No matching clips");
        empty.setTextColor(foreground);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(GONE);
        addView(empty, new LayoutParams(-1, dp(context, 40)));
        adapter.color = foreground;

        watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { search(s.toString()); }
            public void afterTextChanged(Editable text) {}
        };
        if (field instanceof TextView text) {
            // The visible query must be exactly what is searched: bound it like the worker does.
            android.text.InputFilter[] current = text.getFilters();
            android.text.InputFilter[] filters = java.util.Arrays.copyOf(current, current.length + 1);
            filters[current.length] = new android.text.InputFilter.LengthFilter(ClipboardSearchMatcher.MAX_QUERY_LENGTH);
            text.setFilters(filters);
            text.addTextChangedListener(watcher);
        }
    }

    /** Snapshot in native order; never expose unfiltered rows during an active query. */
    public void setClips(List<Clip> value) {
        if (closed) return;
        String query = field instanceof TextView text ? text.getText().toString() : "";
        List<Clip> previouslyVisible = adapter.items;
        clips = List.copyOf(value);
        Map<Long, Clip> index = new HashMap<>();
        List<ClipboardSearchController.Projection> projections = new ArrayList<>();
        for (Clip clip : clips) {
            index.put(clip.id, clip);
            projections.add(new ClipboardSearchController.Projection(clip.id, clip.section, clip.text, false));
        }
        byId = index;
        // Invalidates queued results for the old clipboard snapshot.
        worker.replaceSnapshot(projections);
        if (query.isEmpty()) {
            displayedQuery = "";
            showIds(null);
        } else {
            // While the replacement search is debounced, retain only rows whose
            // identity, section and full text are unchanged. If the visible rows
            // came from a different query, retain none. Never show all clips.
            List<Long> unchanged = new ArrayList<>();
            if (query.equals(displayedQuery)) {
                for (Clip old : previouslyVisible) {
                    Clip updated = index.get(old.id);
                    if (updated != null && old.section == updated.section
                            && old.text.equals(updated.text)) unchanged.add(old.id);
                }
            }
            showIds(unchanged);
            search(query);
        }
    }

    void search(String query) {
        if (closed) return;
        if (query == null || query.isEmpty()) {
            worker.search("", ignored -> {}); // Invalidates older in-flight results.
            showIds(null);
            displayedQuery = "";
            return;
        }
        Map<Long, Clip> snapshot = byId;
        worker.search(query, ids -> {
            if (!closed && byId == snapshot) {
                showIds(ids);
                displayedQuery = query;
            }
        });
    }

    /** Null shows every clip. Order is always the native snapshot order. */
    void showIds(List<Long> ids) {
        List<Clip> visible = new ArrayList<>();
        if (ids == null) visible.addAll(clips);
        else {
            java.util.Set<Long> wanted = new java.util.HashSet<>(ids);
            for (Clip clip : clips) if (wanted.contains(clip.id)) visible.add(clip);
        }
        adapter.items = visible;
        adapter.notifyDataSetChanged();
        empty.setVisibility(visible.isEmpty() ? VISIBLE : GONE);
    }

    List<Clip> visibleClips() { return adapter.items; }

    public void setDeleteEnabled(boolean value) { deleteEnabled = value; if (!value) setPendingDelete(Long.MIN_VALUE); }

    long pendingDelete() { return pendingDelete; }

    private void setPendingDelete(long id) {
        pendingDelete = id;
        adapter.pending = id;
        adapter.notifyDataSetChanged();
    }

    /** Removes a clip the store has confirmed deleted, from the snapshot and the results. */
    public void remove(long id) {
        if (closed) return;
        List<Clip> kept = new ArrayList<>();
        for (Clip clip : clips) if (clip.id != id) kept.add(clip);
        if (kept.size() == clips.size()) return;
        setClips(kept); // Retains the filtered projection; re-search scheduled exactly once.
    }

    /** Detaches the field and stops the worker. The query never outlives the surface. */
    public void close() {
        if (closed) return;
        closed = true;
        if (field instanceof TextView text) {
            text.removeTextChangedListener(watcher);
            text.setText("");
        }
        worker.close();
        clips = List.of(); byId = Map.of();
        adapter.items = List.of();
        adapter.notifyDataSetChanged();
    }

    private static final class Adapter extends BaseAdapter {
        List<Clip> items = List.of();
        int color;
        long pending = Long.MIN_VALUE;
        public int getCount() { return items.size(); }
        public Clip getItem(int position) { return items.get(position); }
        public long getItemId(int position) { return items.get(position).id; }
        public View getView(int position, View convert, ViewGroup parent) {
            TextView row = convert instanceof TextView reused ? reused : new TextView(parent.getContext());
            int pad = dp(parent.getContext(), 12);
            row.setPadding(pad, pad / 2, pad, pad / 2);
            row.setMaxLines(2);
            row.setEllipsize(TextUtils.TruncateAt.END);
            row.setTextColor(color);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            Clip clip = items.get(position);
            // Preview only: the full text stays in the snapshot for search and paste.
            String text = clip.text;
            if (text.length() > 300) text = text.substring(0, Character.isHighSurrogate(text.charAt(299)) ? 299 : 300);
            row.setText(clip.id == pending ? "Tap again to delete · " + text : text);
            return row;
        }
    }

    private static int themeColor(Context context, int attr, int fallback) {
        TypedArray values = context.obtainStyledAttributes(new int[] {attr});
        try { return values.getColor(0, fallback); } finally { values.recycle(); }
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
