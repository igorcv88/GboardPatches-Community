package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.Consumer;

/** Search input belongs to the panel. It never obtains the application's InputConnection. */
public final class ClipboardSearchUiAdapter implements AutoCloseable {
    final EditText input;
    final ImageButton search;
    final LinearLayout keys;
    final TextView empty;
    private final View label;
    private final ViewGroup title, actions, parent;
    private final View recycler;
    private final LinearLayout wrapper;
    private final ViewGroup.LayoutParams originalParams;
    private final int originalIndex, labelVisibility;
    private boolean closed;

    public ClipboardSearchUiAdapter(ViewGroup title, View label, ViewGroup actions,
            View recycler, Consumer<String> query) {
        this.title = title; this.label = label; this.actions = actions;
        this.recycler = recycler;
        Context context = title.getContext();
        labelVisibility = label.getVisibility();
        input = new EditText(context);
        input.setSingleLine(true);
        input.setHint("Search clipboard");
        input.setContentDescription("Search clipboard");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setShowSoftInputOnFocus(false);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ClipboardSearchMatcher.MAX_QUERY_LENGTH)});
        input.setVisibility(View.GONE);
        title.addView(input, new FrameLayout.LayoutParams(-1, -1));
        search = new ImageButton(context);
        search.setImageResource(android.R.drawable.ic_menu_search);
        search.setContentDescription("Search clipboard");
        search.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        actions.addView(search, 0, new LinearLayout.LayoutParams(dp(context, 40), -1));
        search.setOnClickListener(v -> { if (input.getVisibility() == View.VISIBLE) reset(); else open(); });
        parent = (ViewGroup) recycler.getParent();
        originalIndex = parent.indexOfChild(recycler);
        originalParams = recycler.getLayoutParams();
        parent.removeView(recycler);
        wrapper = new LinearLayout(context) {
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN
                        && event.getY() < recycler.getBottom()) input.clearFocus();
                return super.dispatchTouchEvent(event);
            }
        }; wrapper.setOrientation(LinearLayout.VERTICAL);
        parent.addView(wrapper, originalIndex, originalParams);
        wrapper.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1));
        empty = new TextView(context); empty.setText("No matching clips");
        empty.setGravity(Gravity.CENTER); empty.setVisibility(View.GONE);
        wrapper.addView(empty, new LinearLayout.LayoutParams(-1, dp(context, 40)));
        keys = new LinearLayout(context); keys.setOrientation(LinearLayout.VERTICAL);
        keys.setVisibility(View.GONE);
        for (String letters : new String[]{"1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm"}) {
            LinearLayout row = new LinearLayout(context);
            for (int i = 0; i < letters.length(); i++) addKey(row, String.valueOf(letters.charAt(i)));
            keys.addView(row, new LinearLayout.LayoutParams(-1, dp(context, 34)));
        }
        LinearLayout controls = new LinearLayout(context);
        for (String key : new String[]{"_", ".", "-", "Space", "⌫", "Clear", "Done"}) addKey(controls, key);
        keys.addView(controls, new LinearLayout.LayoutParams(-1, dp(context, 34)));
        wrapper.addView(keys, new LinearLayout.LayoutParams(-1, -2));
        input.setOnFocusChangeListener((v, focused) -> keys.setVisibility(focused ? View.VISIBLE : View.GONE));
        input.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_ESCAPE) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) reset();
            return true;
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!closed) query.accept(s.toString());
            }
            public void afterTextChanged(Editable text) {}
        });
    }

    public void open() {
        if (closed) return;
        label.setVisibility(View.GONE); input.setVisibility(View.VISIBLE);
        search.setContentDescription("Close clipboard search");
        input.requestFocus(); keys.setVisibility(View.VISIBLE);
    }

    private void addKey(LinearLayout row, String value) {
        TextView key = new TextView(row.getContext()); key.setText(value);
        key.setGravity(Gravity.CENTER); key.setFocusable(false); key.setContentDescription(value);
        key.setOnClickListener(v -> type(value));
        row.addView(key, new LinearLayout.LayoutParams(0, -1, 1));
    }

    void type(String value) {
        if (closed) return;
        Editable text = input.getText();
        int a = Math.max(0, Math.min(input.getSelectionStart(), input.getSelectionEnd()));
        int b = Math.max(a, Math.max(input.getSelectionStart(), input.getSelectionEnd()));
        switch (value) {
            case "Done" -> { input.clearFocus(); keys.setVisibility(View.GONE); }
            case "Clear" -> input.setText("");
            case "⌫" -> {
                if (a == b && a > 0) a = Character.offsetByCodePoints(text, a, -1);
                text.delete(a, b);
            }
            default -> text.replace(a, b, "Space".equals(value) ? " " : value);
        }
    }

    public void showEmpty(boolean value) { empty.setVisibility(value ? View.VISIBLE : View.GONE); }
    public void reset() {
        input.setText(""); input.clearFocus(); input.setVisibility(View.GONE);
        keys.setVisibility(View.GONE); empty.setVisibility(View.GONE);
        label.setVisibility(labelVisibility); search.setContentDescription("Search clipboard");
    }
    public void close() {
        if (closed) return;
        closed = true; input.setText(""); input.clearFocus();
        title.removeView(input); actions.removeView(search); wrapper.removeView(recycler);
        parent.removeView(wrapper);
        parent.addView(recycler, Math.min(originalIndex, parent.getChildCount()), originalParams);
        label.setVisibility(labelVisibility);
    }
    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
