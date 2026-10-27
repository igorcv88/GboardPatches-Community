package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
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
        this(context, field, heightPx, worker, listener, ClipboardSearchStyle.fallback(context));
    }

    /** Same backdrop, cards, text and header controls as the native clipboard it replaces. */
    ClipboardSearchSurface(Context context, View field, int heightPx,
            ClipboardSearchController worker, Listener listener, ClipboardSearchStyle style) {
        super(context);
        this.field = field; this.worker = worker; this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(style.panelColor);
        setLayoutParams(new ViewGroup.LayoutParams(-1, heightPx));
        int gap = style.cardGap;

        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(gap * 2, gap * 2, gap * 2, gap);
        close = new ImageButton(context, null, 0) {
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                ClipboardSearchDiagnostics.touch("back", event);
                return super.dispatchTouchEvent(event);
            }
        };
        close.setImageDrawable(new BackIcon(style.iconColor, Math.round(style.controlSize * 0.5f)));
        close.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        close.setBackground(pill(style.controlColor, style.controlSize / 2f));
        close.setPadding(0, 0, 0, 0);
        close.setContentDescription("Back to clipboard");
        close.setOnClickListener(v -> {
            if (!this.closed) { ClipboardSearchDiagnostics.state("back-click"); this.listener.onClose(); }
        });
        bar.addView(close, new LayoutParams(style.controlSize, style.controlSize));
        FrameLayout slot = new FrameLayout(context);
        slot.setBackground(pill(style.controlColor, style.controlSize / 2f));
        slot.addView(field, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        LayoutParams slotParams = new LayoutParams(0, style.controlSize, 1);
        slotParams.leftMargin = gap * 2;
        bar.addView(slot, slotParams);
        addView(bar, new LayoutParams(-1, -2));
        if (field instanceof TextView text) {
            // No platform EditText underline: the field is part of the header pill.
            text.setBackground(null);
            text.setPadding(style.controlSize / 2, 0, style.controlSize / 2, 0);
            text.setTextColor(style.iconColor);
            text.setHintTextColor(ClipboardNativeSkin.withAlpha(style.iconColor,
                    Math.round(Color.alpha(style.iconColor) * 0.6f)));
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.textSize);
            if (style.typeface != null) text.setTypeface(style.typeface);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.graphics.drawable.GradientDrawable cursor = new android.graphics.drawable.GradientDrawable();
                cursor.setColor(style.iconColor);
                cursor.setSize(Math.max(1, Math.round(2 * context.getResources().getDisplayMetrics().density)), -1);
                text.setTextCursorDrawable(cursor);
            }
        }

        results = new ListView(context) {
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                ClipboardSearchDiagnostics.touch("list", event);
                return super.dispatchTouchEvent(event);
            }
        };
        results.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            public void onScrollStateChanged(android.widget.AbsListView view, int state) {
                ClipboardSearchDiagnostics.state("scroll-state=" + state);
            }
            public void onScroll(android.widget.AbsListView view, int first, int visible, int total) {
                ClipboardSearchDiagnostics.state("scroll first=" + first + " visible=" + visible + " total=" + total);
            }
        });
        results.setAdapter(adapter);
        results.setDivider(null);
        results.setDividerHeight(0);
        results.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        results.setCacheColorHint(Color.TRANSPARENT);
        results.setPadding(gap * 2, gap / 2, gap * 2, gap * 2);
        results.setClipToPadding(false);
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
            ClipboardSearchDiagnostics.state("result-click");
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
        empty.setTextColor(style.textColor);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(GONE);
        addView(empty, new LayoutParams(-1, dp(context, 40)));
        adapter.style = style;

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

    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        ClipboardSearchDiagnostics.touch("panel", event);
        return super.dispatchTouchEvent(event);
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
        ClipboardSearchStyle style;
        long pending = Long.MIN_VALUE;
        public int getCount() { return items.size(); }
        public Clip getItem(int position) { return items.get(position); }
        public long getItemId(int position) { return items.get(position).id; }
        public View getView(int position, View convert, ViewGroup parent) {
            TextView row = convert instanceof TextView reused ? reused : card(parent.getContext(), style);
            Clip clip = items.get(position);
            // Preview only: the full text stays in the snapshot for search and paste.
            String text = clip.text;
            if (text.length() > 300) text = text.substring(0, Character.isHighSurrogate(text.charAt(299)) ? 299 : 300);
            row.setText(clip.id == pending ? "Tap again to delete · " + text : text);
            return row;
        }
    }

    /** A history card: native color, radius, padding, spacing and text; pressed like a key. */
    private static TextView card(Context context, ClipboardSearchStyle style) {
        TextView row = new TextView(context);
        int half = style.cardGap / 2;
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        int pressed = ClipboardNativeSkin.withAlpha(style.textColor, 0x1f);
        states.addState(new int[]{android.R.attr.state_pressed}, new android.graphics.drawable.LayerDrawable(
                new Drawable[]{pill(style.cardColor, style.cardRadius), pill(pressed, style.cardRadius)}));
        states.addState(new int[0], pill(style.cardColor, style.cardRadius));
        row.setBackground(new android.graphics.drawable.InsetDrawable(states, 0, half, 0, style.cardGap - half));
        row.setPadding(style.cardPaddingH, style.cardPaddingV + half,
                style.cardPaddingH, style.cardPaddingV + style.cardGap - half);
        row.setMaxLines(style.maxLines);
        row.setEllipsize(TextUtils.TruncateAt.END);
        row.setTextColor(style.textColor);
        row.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.textSize);
        if (style.typeface != null) row.setTypeface(style.typeface);
        return row;
    }

    private static Drawable pill(int color, float radius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    /** Back arrow drawn like the search lens: a stroke glyph in the native icon color. */
    static final class BackIcon extends Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int size;
        BackIcon(int color, int size) {
            this.size = size; paint.setColor(color); paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND); paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        }
        public int getIntrinsicWidth() { return size; }
        public int getIntrinsicHeight() { return size; }
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = getBounds(); float unit = Math.min(b.width(), b.height()) / 24f;
            float left = b.exactCenterX() - 12 * unit, top = b.exactCenterY() - 12 * unit;
            paint.setStrokeWidth(2 * unit);
            android.graphics.Path arrow = new android.graphics.Path();
            arrow.moveTo(left + 11 * unit, top + 5 * unit);
            arrow.lineTo(left + 4 * unit, top + 12 * unit);
            arrow.lineTo(left + 11 * unit, top + 19 * unit);
            arrow.moveTo(left + 4 * unit, top + 12 * unit);
            arrow.lineTo(left + 20 * unit, top + 12 * unit);
            canvas.drawPath(arrow, paint);
        }
        public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
