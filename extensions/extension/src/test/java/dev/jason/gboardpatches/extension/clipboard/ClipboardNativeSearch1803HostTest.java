package dev.jason.gboardpatches.extension.clipboard;

import android.app.Activity;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class ClipboardNativeSearch1803HostTest {
    public interface Provider { }
    /** Mirrors Loup: implements Loqa and exposes Z(Loqa;Z)V. */
    public static class Service extends InputMethodService implements Provider {
        final List<Object> providers = new ArrayList<>();
        final List<Boolean> resets = new ArrayList<>();
        boolean editor = true, ignoreRouting;
        void Z(Provider provider, boolean reset) {
            providers.add(provider); resets.add(reset);
            if (!ignoreRouting) editor = provider == null;
        }
        protected boolean aA() { return editor; }
    }
    public static class ChildService extends Service { }
    /** Mirrors EditTextOnKeyboard: a focusable Loqa view with its own lazily created connection. */
    public static class Field extends View implements Provider {
        Object connection = new Object();
        public Field(android.content.Context c) { super(c); setFocusable(true); setFocusableInTouchMode(true); }
        public Object getCurrentInputConnection() { return connection; }
    }

    @Before public void provider() { ClipboardNativeSearch1803.providerInterface = Provider.class.getName(); }
    @After public void reset() { ClipboardNativeSearch1803.inputStarted(null); ClipboardNativeSearch1803.providerInterface = "oqa"; }

    private Field attachedField(Activity activity) {
        FrameLayout root = new FrameLayout(activity);
        Field field = new Field(activity);
        root.addView(field, new FrameLayout.LayoutParams(100, 40));
        activity.setContentView(root);
        return field;
    }

    @Test public void routesLikeJarvisAndRestoresWithTheNativeFinishInputViewCall() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Service service = new ChildService();
        Field field = attachedField(activity);
        ClipboardNativeSearch1803.Host host = new ClipboardNativeSearch1803.Host(service, field);
        assertTrue(host.routeToField());
        assertTrue(field.isFocused());
        assertTrue(host.restoreRouting());
        // Z(field,false) then Z(null,false) ≡ Loup.aC(null,false,true) from onFinishInputView.
        assertEquals(java.util.Arrays.asList(field, null), service.providers);
        assertEquals(List.of(false, false), service.resets);
        assertFalse(host.switchToAlphabet());
    }

    @Test public void themedApplicationContextUsesTheBoundInputServiceInsteadOfFailingSilently() {
        Service service = Robolectric.setupService(Service.class);
        android.content.Context themed = new android.view.ContextThemeWrapper(service.getApplicationContext(), android.R.style.Theme_Material);
        ClipboardNativeSearch1803.inputStarted(service);
        assertSame(service, ClipboardNativeSearch1803.service(themed));
        ClipboardNativeSearch1803.inputStarted(null);
        assertNull(ClipboardNativeSearch1803.service(themed));
    }

    @Test public void fieldMustBeAttachedFocusableAndHaveAConnectionBeforeRouting() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Service service = new Service();
        Field detached = new Field(activity);
        assertFalse(new ClipboardNativeSearch1803.Host(service, detached).routeToField());
        Field noConnection = attachedField(activity);
        noConnection.connection = null;
        assertFalse(new ClipboardNativeSearch1803.Host(service, noConnection).routeToField());
        assertFalse(new ClipboardNativeSearch1803.Host(service, new View(activity)).routeToField());
        assertTrue(service.providers.isEmpty());
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void insetsKeepTheNativeResultAndOnlyAddTheSurface() {
        InputMethodService.Insets region = new InputMethodService.Insets();
        region.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION;
        region.touchableRegion.set(0, 900, 1080, 1600);
        region.visibleTopInsets = 900; region.contentTopInsets = 900;
        ClipboardNativeSearch1803.apply(region, new Rect(0, 500, 1080, 900), new Rect(0, 0, 1080, 1600));
        assertEquals(new Rect(0, 500, 1080, 1600), region.touchableRegion.getBounds());
        assertEquals(500, region.visibleTopInsets);
        assertEquals(900, region.contentTopInsets); // Editor is not resized behind the surface.

        InputMethodService.Insets visible = new InputMethodService.Insets();
        visible.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_VISIBLE;
        visible.visibleTopInsets = 900; visible.contentTopInsets = 900;
        ClipboardNativeSearch1803.apply(visible, new Rect(0, 500, 1080, 900), new Rect(0, 0, 1080, 1600));
        assertEquals(500, visible.visibleTopInsets);
        assertEquals(new Rect(0, 500, 1080, 1600), visible.touchableRegion.getBounds());

        ClipboardNativeSearch1803.beforeComputeInsets(visible);
        ClipboardNativeSearch1803.afterComputeInsets(); // No open search: untouched.
        assertEquals(500, visible.visibleTopInsets);
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void everyNativeModePreservesExactlyItsOriginalRegionPlusTheBoundedPanel() {
        Rect window = new Rect(0, 0, 1080, 1600);
        Rect panel = new Rect(200, 500, 880, 900);
        for (int mode : new int[]{InputMethodService.Insets.TOUCHABLE_INSETS_FRAME,
                InputMethodService.Insets.TOUCHABLE_INSETS_CONTENT,
                InputMethodService.Insets.TOUCHABLE_INSETS_VISIBLE,
                InputMethodService.Insets.TOUCHABLE_INSETS_REGION}) {
            InputMethodService.Insets value = new InputMethodService.Insets();
            value.touchableInsets = mode;
            value.contentTopInsets = 1000; value.visibleTopInsets = 900;
            // Nonrectangular native REGION: preserve holes and floating-keyboard sides.
            value.touchableRegion.set(100, 1000, 500, 1600);
            value.touchableRegion.op(new Rect(600, 1100, 900, 1600), android.graphics.Region.Op.UNION);
            android.graphics.Region expected = new android.graphics.Region();
            if (mode == InputMethodService.Insets.TOUCHABLE_INSETS_REGION) expected.set(value.touchableRegion);
            else expected.set(0, mode == InputMethodService.Insets.TOUCHABLE_INSETS_FRAME ? 0
                    : mode == InputMethodService.Insets.TOUCHABLE_INSETS_CONTENT ? 1000 : 900, 1080, 1600);
            expected.op(panel, android.graphics.Region.Op.UNION);
            ClipboardNativeSearch1803.apply(value, panel, window);
            android.graphics.Region difference = new android.graphics.Region(expected);
            difference.op(value.touchableRegion, android.graphics.Region.Op.XOR);
            assertTrue("mode=" + mode + " diff=" + difference, difference.isEmpty());
            assertEquals(InputMethodService.Insets.TOUCHABLE_INSETS_REGION, value.touchableInsets);
            assertEquals(1000, value.contentTopInsets);
            assertTrue(value.touchableRegion.contains(300, 600));
            if (mode != InputMethodService.Insets.TOUCHABLE_INSETS_FRAME)
                assertFalse(value.touchableRegion.contains(50, 600)); // App beside panel stays reachable.
        }
    }

    @Test public void backAndLifecycleHooksAreInertWithoutAnOpenSearch() {
        assertFalse(ClipboardNativeSearch1803.onBackKey(KeyEvent.KEYCODE_BACK,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));
        assertFalse(ClipboardNativeSearch1803.onBackKey(KeyEvent.KEYCODE_A,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)));
        ClipboardNativeSearch1803.end();
        assertFalse(ClipboardNativeSearch1803.isOpen());
    }

    @Test public void searchIsEnabledButMissingNativeActionsFailClosed() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertTrue(ClipboardNativeSearch1803.available());
        assertFalse(ClipboardNativeSearch1803.open(null, new View(activity), List.of()));
        assertFalse(ClipboardNativeSearch1803.isOpen());
    }

    /** Succeeds every step; records calls. Optionally ends the session from inside the switch. */
    static class FakeHost implements ClipboardNativeSearchSession.Host {
        final List<String> calls = new ArrayList<>();
        boolean endDuringSwitch;
        public boolean showSurface() { calls.add("surface"); return true; }
        public void hideSurface() { calls.add("hide"); }
        public boolean switchToAlphabet() {
            calls.add("alphabet");
            if (endDuringSwitch) ClipboardNativeSearch1803.end(); // Lifecycle callback during the switch.
            return true;
        }
        public boolean routeToField() { calls.add("route"); return true; }
        public boolean restoreRouting() { calls.add("restore"); return true; }
        public void returnToClipboard() { calls.add("clipboard"); }
        public boolean commitToEditor(String text) { calls.add("commit"); return true; }
    }

    @Test public void arrowCallbackRestoresRoutingAndReturnsToTheNativeClipboard() throws Exception {
        Service service = Robolectric.setupService(Service.class);
        FakeHost host = new FakeHost();
        assertTrue(ClipboardNativeSearch1803.start(service, new Field(service), List.of(), host));
        java.lang.reflect.Field member = ClipboardNativeSearch1803.class.getDeclaredField("surface");
        member.setAccessible(true);
        ClipboardSearchSurface panel = (ClipboardSearchSurface) member.get(null);
        panel.close.performClick();
        assertFalse(ClipboardNativeSearch1803.isOpen());
        assertEquals(List.of("surface", "alphabet", "route", "restore", "hide", "clipboard"), host.calls);
        panel.close.performClick();
        assertEquals(6, host.calls.size()); // Detached arrow cannot return a second time.
    }

    @Test public void firstBackClosesOnlyTheSearchAndReturnsToTheClipboard() {
        Service service = Robolectric.setupService(Service.class);
        FakeHost host = new FakeHost();
        assertTrue(ClipboardNativeSearch1803.start(service, new Field(service), List.of(), host));
        assertTrue(ClipboardNativeSearch1803.isOpen());
        assertTrue(ClipboardNativeSearch1803.onBackKey(KeyEvent.KEYCODE_BACK,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));
        assertTrue(ClipboardNativeSearch1803.isOpen());
        assertTrue(ClipboardNativeSearch1803.onBackKey(KeyEvent.KEYCODE_BACK,
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)));
        assertFalse(ClipboardNativeSearch1803.isOpen());
        assertEquals(List.of("surface", "alphabet", "route", "restore", "hide", "clipboard"), host.calls);
        // Second Back follows the native path.
        assertFalse(ClipboardNativeSearch1803.onBackKey(KeyEvent.KEYCODE_BACK,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));
    }

    @Test public void lifecycleEndDuringTheKeyboardSwitchClosesRightAfterOpening() {
        Service service = Robolectric.setupService(Service.class);
        FakeHost host = new FakeHost();
        host.endDuringSwitch = true;
        assertFalse(ClipboardNativeSearch1803.start(service, new Field(service), List.of(), host));
        assertFalse(ClipboardNativeSearch1803.isOpen());
        assertEquals(List.of("surface", "alphabet", "route", "restore", "hide"), host.calls);
    }

    @Test public void lifecycleEndRestoresWithoutReturningToTheClipboard() {
        Service service = Robolectric.setupService(Service.class);
        FakeHost host = new FakeHost();
        assertTrue(ClipboardNativeSearch1803.start(service, new Field(service), List.of(), host));
        ClipboardNativeSearch1803.end();
        assertFalse(ClipboardNativeSearch1803.isOpen());
        assertEquals(List.of("surface", "alphabet", "route", "restore", "hide"), host.calls);
    }

    @Test public void nativeReturnToTheClipboardEndsSearchWithoutLeavingTheRowOrSwitchingAgain() {
        Service service = Robolectric.setupService(Service.class);
        FakeHost host = new FakeHost();
        assertTrue(ClipboardNativeSearch1803.start(service, new Field(service), List.of(), host));
        // ClipboardKeyboard.e() while search is open: the toolbar/native path brought it back.
        ClipboardNativeSearch1803.clipboardShown();
        assertFalse(ClipboardNativeSearch1803.isOpen());
        assertEquals(List.of("surface", "alphabet", "route", "restore", "hide"), host.calls);
        ClipboardNativeSearch1803.clipboardShown(); // Idempotent, inert without a session.
        assertEquals(5, host.calls.size());
    }

    @Test public void ourOwnReturnToTheClipboardDoesNotReenterTheClose() {
        Service service = Robolectric.setupService(Service.class);
        FakeHost host = new FakeHost() {
            @Override public void returnToClipboard() { super.returnToClipboard(); ClipboardNativeSearch1803.clipboardShown(); }
        };
        assertTrue(ClipboardNativeSearch1803.start(service, new Field(service), List.of(), host));
        assertTrue(ClipboardNativeSearch1803.onBackKey(KeyEvent.KEYCODE_BACK,
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)));
        assertFalse(ClipboardNativeSearch1803.isOpen());
        assertEquals(List.of("surface", "alphabet", "route", "restore", "hide", "clipboard"), host.calls);
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void searchThatNeverFindsTheKeyboardIsRolledBackInsteadOfLeftAtTheTop() throws Exception {
        Service service = Robolectric.setupService(Service.class);
        FakeHost host = new FakeHost();
        assertTrue(ClipboardNativeSearch1803.start(service, new Field(service), List.of(), host));
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        java.lang.reflect.Field surfaceField = ClipboardNativeSearch1803.class.getDeclaredField("surface");
        surfaceField.setAccessible(true);
        View shown = (View) surfaceField.get(null);
        activity.setContentView(shown);
        shown.setAlpha(0f); // What the slot keeps while waiting for a keyboard root.
        assertTrue(shown.isShown());
        InputMethodService.Insets insets = new InputMethodService.Insets();
        insets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION;
        insets.touchableRegion.set(0, 900, 1080, 1600); insets.visibleTopInsets = 900;
        ClipboardNativeSearch1803.beforeComputeInsets(insets);
        ClipboardNativeSearch1803.afterComputeInsets();
        assertEquals(new Rect(0, 900, 1080, 1600), insets.touchableRegion.getBounds()); // Not touchable.
        assertEquals(900, insets.visibleTopInsets);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(ClipboardNativeSearch1803.ANCHOR_TIMEOUT_MS + 1));
        assertFalse(ClipboardNativeSearch1803.isOpen());
        assertEquals(List.of("surface", "alphabet", "route", "restore", "hide", "clipboard"), host.calls);
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void touchRegionIsConfirmedOnlyAfterAVisibleSearchPanelIsIncludedAndResetsOnClose() throws Exception {
        Service service = Robolectric.setupService(Service.class);
        FakeHost host = new FakeHost();
        assertTrue(ClipboardNativeSearch1803.start(service, new Field(service), List.of(), host));
        java.lang.reflect.Field member = ClipboardNativeSearch1803.class.getDeclaredField("surface");
        member.setAccessible(true);
        View panel = (View) member.get(null);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        root.addView(panel, new FrameLayout.LayoutParams(1080, 600));
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 1600);
        View decor = panel.getRootView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1080, 1600);
        InputMethodService.Insets insets = new InputMethodService.Insets();
        insets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION;
        insets.touchableRegion.set(0, 600, 1080, 1600);
        insets.visibleTopInsets = 600;
        assertFalse(ClipboardNativeSearch1803.touchRegionComputedFor(panel));
        panel.setAlpha(0f);
        ClipboardNativeSearch1803.beforeComputeInsets(insets);
        ClipboardNativeSearch1803.afterComputeInsets();
        assertFalse(ClipboardNativeSearch1803.touchRegionComputedFor(panel));
        panel.setAlpha(1f);
        ClipboardNativeSearch1803.beforeComputeInsets(insets);
        ClipboardNativeSearch1803.afterComputeInsets();
        assertTrue(ClipboardNativeSearch1803.touchRegionComputedFor(panel));
        ClipboardNativeSearch1803.end();
        assertFalse(ClipboardNativeSearch1803.touchRegionComputedFor(panel));
    }

    @Test public void failedStartRestoresRoutingAndLeavesNoSurfaceOrSession() {
        Service service = Robolectric.setupService(Service.class);
        Field field = new Field(service);
        assertFalse(ClipboardNativeSearch1803.start(service, field,
                List.of(new ClipboardSearchSurface.Clip(1, ClipboardSearchController.Section.RECENT, "a")),
                new ClipboardNativeSearch1803.Host(service, field)));
        assertFalse(ClipboardNativeSearch1803.isOpen());
        assertTrue(service.providers.isEmpty()); // Alphabet switch is unbound: never routed.
    }
    @Test public void nativeNoOpRoutingIsNotReportedAsSuccess() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Service service = new Service(); service.ignoreRouting = true;
        ClipboardNativeSearch1803.Host host = new ClipboardNativeSearch1803.Host(service, attachedField(activity));
        assertFalse(host.routeToField());
        service.editor = false;
        assertFalse(host.restoreRouting());
    }

    @Test public void failedVisibilityCheckRollsBackToClipboardAndDoesNotPoisonTheNextSession() throws Exception {
        Service service = Robolectric.setupService(Service.class);
        FakeHost first = new FakeHost();
        assertTrue(ClipboardNativeSearch1803.start(service, new Field(service), List.of(), first));
        // FakeHost does not own a window. Attach the surface so View.postDelayed's
        // attach queue runs, then reproduce the hidden ancestor from the device dump.
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        java.lang.reflect.Field surfaceField = ClipboardNativeSearch1803.class.getDeclaredField("surface");
        surfaceField.setAccessible(true);
        View shown = (View) surfaceField.get(null);
        activity.setContentView(shown);
        shown.setVisibility(View.GONE);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(ClipboardNativeSearch1803.ANCHOR_TIMEOUT_MS + 1));
        assertFalse(ClipboardNativeSearch1803.isOpen());
        assertEquals(List.of("surface", "alphabet", "route", "restore", "hide", "clipboard"), first.calls);
        FakeHost next = new FakeHost();
        assertTrue(ClipboardNativeSearch1803.start(service, new Field(service), List.of(), next));
        ClipboardNativeSearch1803.end();
        assertEquals(List.of("surface", "alphabet", "route", "restore", "hide"), next.calls);
    }

}
