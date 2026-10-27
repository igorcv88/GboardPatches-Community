package dev.jason.gboardpatches.extension.clipboard;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Owns only the framework candidates slot; never replaces/reparents Gboard's InputView.
 * The exact device dump has candidatesArea inside an INVISIBLE fullscreenArea, beside
 * inputArea. INVISIBLE still measures: an unbounded placeholder here starves InputView.
 *
 * <p>inputArea and InputView span the whole window (2992 px on the S25 Ultra dump) and
 * draw the keyboard at their bottom, while candidatesArea is the window's first row. The
 * surface is therefore anchored to the native keyboard root: its bottom edge follows the
 * keyboard's top edge on every layout, and the space above it becomes the top margin.
 * inputArea then measures exactly to the keyboard, so slot and keyboard are adjacent rows
 * of the same IME layout. Nothing is offset by a fixed amount.
 */
final class ClipboardSearchWindowSlot implements AutoCloseable {
    /** Gboard's keyboard root, as tagged by its theme engine (docked, then floating). */
    static final String[] KEYBOARD_TAGS = {".keyboard-base-area.keyboard-outline", ".keyboard-base-area"};

    private final ViewGroup area;
    private final View parent;
    private final int areaVisibility, parentVisibility;
    private final List<View> original = new ArrayList<>();
    private final List<ViewGroup.LayoutParams> params = new ArrayList<>();
    private final ViewTreeObserver.OnPreDrawListener anchor = this::onPreDraw;
    private ViewTreeObserver observer;
    private WeakReference<View> keyboard = new WeakReference<>(null);
    private View owned;
    private int preferredHeight, settling;
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
        preferredHeight = height;
        for (View view : original) area.removeView(view);
        // Do not use setCandidatesView: it overwrites height with WRAP_CONTENT.
        area.addView(surface, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        parent.setVisibility(View.VISIBLE);
        area.setVisibility(View.VISIBLE);
        area.requestLayout();
        observer = area.getViewTreeObserver();
        observer.addOnPreDrawListener(anchor);
    }

    /** A frame drawn before anchoring would flash the surface at the window top. */
    private boolean onPreDraw() {
        if (!anchorToKeyboard()) { settling = 0; return true; }
        // The fixed point is reached in one pass when the keyboard is bottom- or top-aligned;
        // never withhold more than a few frames for any other native arrangement.
        return ++settling > 3;
    }

    /**
     * Places the surface's bottom on the keyboard's top and its sides on the keyboard's sides,
     * limiting its height to the space actually available. Returns true if it moved.
     */
    boolean anchorToKeyboard() {
        if (closed || owned == null || owned.getParent() != area || !area.isAttachedToWindow()) return false;
        View root = keyboard();
        if (root == null) return false;
        int[] at = new int[2];
        root.getLocationInWindow(at);
        int keyboardLeft = at[0], keyboardTop = at[1];
        area.getLocationInWindow(at);
        // The area's own top does not depend on the margin set below.
        int available = keyboardTop - at[1] - area.getPaddingTop();
        if (available <= 0) return false; // Floating keyboard over the slot: keep the stock row.
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) owned.getLayoutParams();
        int height = Math.min(preferredHeight, available);
        int top = available - height;
        int left = Math.max(0, keyboardLeft - at[0] - area.getPaddingLeft());
        int width = root.getWidth() < area.getWidth() ? root.getWidth() : ViewGroup.LayoutParams.MATCH_PARENT;
        if (lp.height == height && lp.topMargin == top && lp.leftMargin == left && lp.width == width) return false;
        lp.height = height; lp.topMargin = top; lp.leftMargin = left; lp.width = width;
        owned.setLayoutParams(lp);
        return true;
    }

    /** Cached native keyboard root; re-resolved whenever Gboard replaces or hides it. */
    private View keyboard() {
        View cached = keyboard.get();
        if (usable(cached)) return cached;
        View found = null;
        for (String tag : KEYBOARD_TAGS) {
            found = find(area.getRootView(), tag);
            if (found != null) break;
        }
        keyboard = new WeakReference<>(found);
        return found;
    }

    private boolean usable(View view) {
        if (view == null || !view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        for (String tag : KEYBOARD_TAGS) if (tag.equals(view.getTag())) return !inside(view, area);
        return false;
    }

    private View find(View view, String tag) {
        if (tag.equals(view.getTag()) && usable(view)) return view;
        if (view == area || !(view instanceof ViewGroup group)) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = find(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean inside(View view, View ancestor) {
        for (View current = view; current != null; current = current.getParent() instanceof View p ? p : null) {
            if (current == ancestor) return true;
        }
        return false;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (observer != null) {
            if (observer.isAlive()) observer.removeOnPreDrawListener(anchor);
            else area.getViewTreeObserver().removeOnPreDrawListener(anchor);
            observer = null;
        }
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
