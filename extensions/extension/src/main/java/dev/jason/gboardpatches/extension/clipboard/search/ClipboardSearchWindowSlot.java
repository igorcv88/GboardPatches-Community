package dev.jason.gboardpatches.extension.clipboard;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;

/** Owns only the framework candidates slot; never replaces/reparents Gboard's InputView.
 * The exact device dump has candidatesArea inside an INVISIBLE fullscreenArea, beside
 * inputArea. INVISIBLE still measures: an unbounded placeholder here starves InputView.
 */
final class ClipboardSearchWindowSlot implements AutoCloseable {
    private final ViewGroup area;
    private final View parent;
    private final int areaVisibility, parentVisibility;
    private final List<View> original = new ArrayList<>();
    private final List<ViewGroup.LayoutParams> params = new ArrayList<>();
    private View owned;
    private boolean closed;

    static ClipboardSearchWindowSlot resolve(InputMethodService ime) {
        if (ime.isFullscreenMode() || ime.getWindow() == null || ime.getWindow().getWindow() == null) return null;
        View decor = ime.getWindow().getWindow().getDecorView();
        int id = ime.getResources().getIdentifier("candidatesArea", "id", "android");
        if (id <= 0) return null;
        View candidate = decor.findViewById(id);
        if (!(candidate instanceof FrameLayout area) || !(area.getParent() instanceof ViewGroup parent)) return null;
        int inputId = ime.getResources().getIdentifier("inputArea", "id", "android");
        View input = inputId <= 0 ? null : decor.findViewById(inputId);
        // Reviewed framework structure: fullscreenArea and inputArea are siblings.
        if (input == null || input.getParent() != parent.getParent()) return null;
        return new ClipboardSearchWindowSlot(area, parent);
    }

    ClipboardSearchWindowSlot(ViewGroup area, View parent) {
        this.area = area; this.parent = parent;
        areaVisibility = area.getVisibility(); parentVisibility = parent.getVisibility();
        for (int i = 0; i < area.getChildCount(); i++) {
            View view = area.getChildAt(i); original.add(view); params.add(view.getLayoutParams());
        }
    }

    boolean wasShown() { return areaVisibility == View.VISIBLE; }

    void attach(View surface, int height) {
        if (closed || owned != null || height <= 0 || surface.getParent() != null) throw new IllegalStateException("invalid candidates-slot ownership");
        owned = surface;
        for (View view : original) area.removeView(view);
        // Do not use setCandidatesView: it overwrites height with WRAP_CONTENT.
        area.addView(surface, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        parent.setVisibility(View.VISIBLE);
        area.setVisibility(View.VISIBLE);
        area.requestLayout();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (owned != null && owned.getParent() == area) area.removeView(owned);
        owned = null;
        // Preserve any unrelated views a native owner added in the meantime.
        for (int i = 0; i < original.size(); i++) {
            View view = original.get(i);
            if (view.getParent() == null) area.addView(view, Math.min(i, area.getChildCount()), params.get(i));
        }
        area.setVisibility(areaVisibility);
        parent.setVisibility(parentVisibility);
        area.requestLayout();
    }
}
