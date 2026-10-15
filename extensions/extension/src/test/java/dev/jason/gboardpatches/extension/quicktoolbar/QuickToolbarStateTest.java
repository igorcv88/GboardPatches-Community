package dev.jason.gboardpatches.extension.quicktoolbar;
import org.junit.Test;
import static org.junit.Assert.*;
import static dev.jason.gboardpatches.extension.quicktoolbar.QuickToolbarState.*;

public class QuickToolbarStateTest {
    @Test public void favoritesToggleNeverRequestsFullPanelWhileCandidatesActive() {
        QuickToolbarState s = new QuickToolbarState();
        s.update(true, true, true); assertEquals(Mode.SUGGESTIONS, s.mode());
        assertEquals(Click.SHOW_FAVORITES, s.click()); assertEquals(Mode.QUICK_TOOLS, s.mode());
        s.update(true, true, true); assertEquals(Mode.QUICK_TOOLS, s.mode());
        assertEquals(Click.SHOW_CANDIDATES, s.click());
    }
    @Test public void candidateInvalidationContextLossAndFeatureOffRestoreStock() {
        for (boolean[] state : new boolean[][] {{true,true,false},{true,false,true},{false,true,true}}) {
            QuickToolbarState s = new QuickToolbarState(); s.update(true,true,true); s.click();
            s.update(state[0],state[1],state[2]);
            assertEquals(Mode.STOCK_TOOLBAR, s.mode()); assertEquals(Click.DELEGATE_STOCK,s.click());
            s.update(true,true,true); assertEquals(Mode.SUGGESTIONS,s.mode());
            s.detach(); assertEquals(Mode.STOCK_TOOLBAR,s.mode());
        }
    }
}
