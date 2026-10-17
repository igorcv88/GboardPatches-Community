package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

/**
 * Search button in the native clipboard header. Typing happens on the native alphabet
 * keyboard with the results surface above it ({@link ClipboardNativeSearch1803}); the panel
 * itself has no input field and no internal keypad.
 */
public final class ClipboardSearchUiAdapter implements AutoCloseable {
    final ImageButton search;
    private final ViewGroup actions;
    private boolean closed;

    public ClipboardSearchUiAdapter(ViewGroup actions, Runnable onSearch) {
        this.actions = actions;
        Context context = actions.getContext();
        search = new ImageButton(context);
        search.setImageResource(android.R.drawable.ic_menu_search);
        search.setContentDescription("Search clipboard");
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setOnClickListener(v -> { if (!closed) onSearch.run(); });
        actions.addView(search, 0, new LinearLayout.LayoutParams(
                Math.round(40 * context.getResources().getDisplayMetrics().density), -1));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        actions.removeView(search);
    }

    boolean attached() { return !closed && search.getParent() == actions; }
}
