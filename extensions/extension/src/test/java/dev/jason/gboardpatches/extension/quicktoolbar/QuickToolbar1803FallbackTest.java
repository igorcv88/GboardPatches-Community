package dev.jason.gboardpatches.extension.quicktoolbar;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;
import android.os.Looper;
import org.robolectric.Shadows;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** The shortcuts key must not open the full panel while suggestions are active, even unbound. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class QuickToolbar1803FallbackTest {
    public static class Action { public int c; Action(int c) { this.c = c; } }
    public static class Event { final Action action; Event(int code) { action = new Action(code); } public Object g() { return action; } }

    private Object manager;
    private final Object owner = new Object();

    @Before public void setUp() throws Exception {
        QuickToolbar1803Runtime.clear();
        QuickToolbar1803Runtime.enabledOverride = true;
        manager = Class.forName("QuickToolbarFakeManager").getDeclaredConstructor().newInstance();
    }

    @After public void tearDown() {
        QuickToolbar1803Runtime.enabledOverride = null;
        QuickToolbar1803Runtime.clear();
    }

    private int count(String name) throws Exception {
        Field field = manager.getClass().getField(name);
        return field.getInt(manager);
    }

    @Test public void activeSuggestionsToggleFavoritesInsteadOfFullPanel() throws Exception {
        QuickToolbar1803Runtime.onCandidateList(owner, List.of("one", "two"));
        assertTrue(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        assertEquals(1, count("shown"));
        assertNotNull(manager.getClass().getField("lastReason").get(manager));
        // Second press returns to suggestions instead of opening anything.
        assertTrue(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        assertEquals(1, count("hidden"));
        assertEquals(1, count("shown"));
    }

    public static final class NonViewController {
        public Object j = new Object();
    }

    @Test public void undoRestoresFavoritesAfterCandidateRedrawWithoutAnotherTap() throws Exception {
        QuickToolbar1803Runtime.onCandidateList(owner, List.of("one", "two"));
        assertTrue(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        assertEquals(1, count("shown"));
        // Broken binding is normal on 18.0.3. It must not clear the selected fallback.
        QuickToolbar1803Runtime.onControllerView(new NonViewController());
        QuickToolbar1803Runtime.onCandidateList(owner, List.of("new suggestions"));
        QuickToolbar1803Runtime.afterSuccessfulHistoryAction();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS);
        assertEquals(2, count("shown"));
        // One press now returns to suggestions: no phantom first tap.
        assertTrue(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        assertEquals(1, count("hidden"));
    }

    @Test public void pendingUndoRestoreCannotReopenFavoritesAfterUserTogglesOff() throws Exception {
        QuickToolbar1803Runtime.onCandidateList(owner, List.of("one"));
        assertTrue(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        QuickToolbar1803Runtime.afterSuccessfulHistoryAction();
        assertTrue(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS);
        assertEquals(1, count("shown"));
        assertEquals(1, count("hidden"));
    }

    @Test public void newSessionCancelsPendingFavoritesRestore() throws Exception {
        QuickToolbar1803Runtime.onCandidateList(owner, List.of("one"));
        assertTrue(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        QuickToolbar1803Runtime.afterSuccessfulHistoryAction();
        QuickToolbar1803Runtime.reset();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS);
        assertEquals(1, count("shown"));
    }

    @Test public void normalKeyboardKeepsStockFullPanel() throws Exception {
        assertFalse(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        QuickToolbar1803Runtime.onCandidateList(owner, List.of());
        assertFalse(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        assertEquals(0, count("shown"));
        assertEquals(0, count("hidden"));
    }

    @Test public void disabledFeatureAndOtherKeysAreUntouched() throws Exception {
        QuickToolbar1803Runtime.onCandidateList(owner, List.of("one"));
        assertFalse(QuickToolbar1803Runtime.handleEvent(manager, new Event(-10001)));
        QuickToolbar1803Runtime.enabledOverride = false;
        assertFalse(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        assertEquals(0, count("shown"));
    }

    @Test public void newInputSessionForgetsSuggestions() throws Exception {
        QuickToolbar1803Runtime.onCandidateList(owner, List.of("one"));
        QuickToolbar1803Runtime.reset();
        assertFalse(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
    }

    @Test public void managerWithoutReviewedMethodFallsBackToStock() {
        QuickToolbar1803Runtime.onCandidateList(owner, List.of("one"));
        assertFalse(QuickToolbar1803Runtime.handleEvent(new Object(), new Event(-40005)));
    }
}
