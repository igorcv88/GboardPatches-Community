package dev.jason.gboardpatches.extension.quicktoolbar;

/** Policy only. Attach exclusively to a reviewed 18.0.3 candidate/toolbar view adapter. */
public final class QuickToolbarState {
    public enum Mode { STOCK_TOOLBAR, SUGGESTIONS, QUICK_TOOLS }
    public enum Click { DELEGATE_STOCK, SHOW_FAVORITES, SHOW_CANDIDATES }
    private boolean enabled, activeContext, candidates, expanded;

    public void update(boolean enabled, boolean activeContext, boolean hasCandidates) {
        this.enabled = enabled;
        this.activeContext = activeContext;
        this.candidates = hasCandidates;
        if (!enabled || !activeContext || !hasCandidates) expanded = false;
    }

    public Mode mode() {
        if (!enabled || !activeContext || !candidates) return Mode.STOCK_TOOLBAR;
        return expanded ? Mode.QUICK_TOOLS : Mode.SUGGESTIONS;
    }

    public Click click() {
        if (mode() == Mode.STOCK_TOOLBAR) return Click.DELEGATE_STOCK;
        expanded = !expanded;
        return expanded ? Click.SHOW_FAVORITES : Click.SHOW_CANDIDATES;
    }

    public void detach() { update(false, false, false); }
}
