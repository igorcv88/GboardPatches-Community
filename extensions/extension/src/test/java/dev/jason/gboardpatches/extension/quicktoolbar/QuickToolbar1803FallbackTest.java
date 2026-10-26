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

    public enum Surface { HEADER, WIDGET }
    public static class HeaderOwner { public Surface t = Surface.HEADER; }

    @Test public void historyUpdatesCannotRequestTheCandidateSurfaceOrReopenTools() throws Exception {
        HeaderOwner header = new HeaderOwner();
        QuickToolbar1803Runtime.onCandidateList(header, List.of("one"));
        assertTrue(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        for (int i = 0; i < 5; i++) {
            QuickToolbar1803Runtime.onCandidateList(header, List.of("redraw"));
            assertTrue(QuickToolbar1803Runtime.keepTools(header));
            QuickToolbar1803Runtime.afterSuccessfulHistoryAction();
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, count("shown"));
        assertEquals(0, count("hidden"));
        assertFalse(QuickToolbar1803Runtime.keepTools(new HeaderOwner()));
        header.t = Surface.WIDGET;
        assertFalse(QuickToolbar1803Runtime.keepTools(header));
    }

    @Test public void typingReleasesToolsBeforeCandidateRenderingAndDoesNotConsumeTheKey() throws Exception {
        HeaderOwner header = new HeaderOwner();
        QuickToolbar1803Runtime.onCandidateList(header, List.of("one"));
        assertTrue(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        assertFalse(QuickToolbar1803Runtime.handleEvent(manager, new Event('á')));
        assertFalse(QuickToolbar1803Runtime.keepTools(header));
        QuickToolbar1803Runtime.onCandidateList(header, List.of("typed"));
        QuickToolbar1803Runtime.afterSuccessfulHistoryAction();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, count("hidden"));
        assertEquals(1, count("shown"));
        assertTrue(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        assertEquals(2, count("shown"));
    }

    @Test public void toolAndClipboardActionsDoNotReleaseTheSelectedToolbar() throws Exception {
        HeaderOwner header = new HeaderOwner();
        QuickToolbar1803Runtime.onCandidateList(header, List.of("one"));
        assertTrue(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        assertFalse(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40003)));
        assertTrue(QuickToolbar1803Runtime.keepTools(header));
        assertEquals(0, count("hidden"));
    }

    public static class FrameOwner extends HeaderOwner { public android.view.View c; }
    private android.view.View row;
    private android.widget.FrameLayout frame;
    private FrameOwner frameOwner() throws Exception {
        android.app.Activity activity = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        frame = new android.widget.FrameLayout(activity);
        FrameOwner owner = new FrameOwner(); owner.c = new android.view.View(activity);
        row = new android.view.View(activity);
        frame.addView(owner.c, new android.widget.FrameLayout.LayoutParams(200, 48));
        frame.addView(row, new android.widget.FrameLayout.LayoutParams(200, 48));
        activity.setContentView(frame);
        android.view.View decor = activity.getWindow().getDecorView();
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 400, 400);
        frame.layout(0, 0, 300, 100);
        owner.c.layout(0, 0, 200, 48); row.layout(0, 0, 200, 48);
        Object holder = manager.getClass().getField("s").get(manager);
        holder.getClass().getField("j").set(holder, row);
        QuickToolbar1803Runtime.onCandidateList(owner, List.of("one"));
        assertTrue(QuickToolbar1803Runtime.handleEvent(manager, new Event(-40005)));
        return owner;
    }

    @Test public void historyClearRestoresTheActualRowBeforeAnyBlankFrameIsDrawn() throws Exception {
        FrameOwner owner = frameOwner();
        for (int i = 0; i < 5; i++) {
            row.setVisibility(android.view.View.GONE); // Native arbitration after Undo/Redo.
            QuickToolbar1803Runtime.onCandidateList(owner, List.of());
            QuickToolbar1803Runtime.afterSuccessfulHistoryAction();
            assertTrue(frame.getViewTreeObserver().dispatchOnPreDraw()); // Cancel this blank frame.
            assertEquals(android.view.View.VISIBLE, row.getVisibility());
            assertFalse(frame.getViewTreeObserver().dispatchOnPreDraw()); // Restored row can draw.
        }
        assertEquals(6, count("shown"));
        QuickToolbar1803Runtime.handleEvent(manager, new Event('x'));
        assertFalse(frame.getViewTreeObserver().dispatchOnPreDraw());
        assertEquals(6, count("shown")); // No restore after typing released ownership.
    }

    @Test public void visibleToolsAreNotRedispatchedAndClipboardRetainsItsOwnHeader() throws Exception {
        frameOwner();
        assertFalse(frame.getViewTreeObserver().dispatchOnPreDraw());
        assertEquals(1, count("shown"));
        QuickToolbar1803Runtime.clipboardPanel(true);
        row.setVisibility(android.view.View.GONE);
        assertFalse(frame.getViewTreeObserver().dispatchOnPreDraw());
        assertEquals(1, count("shown"));
        QuickToolbar1803Runtime.clipboardPanel(false);
        assertTrue(frame.getViewTreeObserver().dispatchOnPreDraw());
        assertEquals(2, count("shown"));
    }

    @Test public void aRefusingNativeHolderCannotBlockDrawingInALoop() throws Exception {
        frameOwner(); row.setVisibility(android.view.View.GONE);
        manager.getClass().getField("refuseShow").setBoolean(manager, true);
        assertTrue(frame.getViewTreeObserver().dispatchOnPreDraw());
        assertTrue(frame.getViewTreeObserver().dispatchOnPreDraw());
        assertFalse(frame.getViewTreeObserver().dispatchOnPreDraw());
        assertFalse(frame.getViewTreeObserver().dispatchOnPreDraw());
        assertEquals(3, count("shown"));
        QuickToolbar1803Runtime.reset();
        assertFalse(frame.getViewTreeObserver().dispatchOnPreDraw());
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
