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
        public boolean aA() { return editor; }
    }
    /** Mirrors EditTextOnKeyboard: a focusable Loqa view with its own lazily created connection. */
    public static class Field extends View implements Provider {
        Object connection = new Object();
        public Field(android.content.Context c) { super(c); setFocusable(true); setFocusableInTouchMode(true); }
        public Object getCurrentInputConnection() { return connection; }
    }

    @Before public void provider() { ClipboardNativeSearch1803.providerInterface = Provider.class.getName(); }
    @After public void reset() { ClipboardNativeSearch1803.providerInterface = "oqa"; }

    private Field attachedField(Activity activity) {
        FrameLayout root = new FrameLayout(activity);
        Field field = new Field(activity);
        root.addView(field, new FrameLayout.LayoutParams(100, 40));
        activity.setContentView(root);
        return field;
    }

    @Test public void routesLikeJarvisAndRestoresWithTheNativeFinishInputViewCall() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Service service = new Service();
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
        ClipboardNativeSearch1803.apply(region, new Rect(0, 500, 1080, 900));
        assertEquals(new Rect(0, 500, 1080, 1600), region.touchableRegion.getBounds());
        assertEquals(500, region.visibleTopInsets);
        assertEquals(900, region.contentTopInsets); // Editor is not resized behind the surface.

        InputMethodService.Insets visible = new InputMethodService.Insets();
        visible.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_VISIBLE;
        visible.visibleTopInsets = 900; visible.contentTopInsets = 900;
        ClipboardNativeSearch1803.apply(visible, new Rect(0, 500, 1080, 900));
        assertEquals(500, visible.visibleTopInsets);
        assertTrue(visible.touchableRegion.isEmpty());

        ClipboardNativeSearch1803.beforeComputeInsets(visible);
        ClipboardNativeSearch1803.afterComputeInsets(); // No open search: untouched.
        assertEquals(500, visible.visibleTopInsets);
    }

    @Test public void backAndLifecycleHooksAreInertWithoutAnOpenSearch() {
        assertFalse(ClipboardNativeSearch1803.onBackKey(KeyEvent.KEYCODE_BACK,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));
        assertFalse(ClipboardNativeSearch1803.onBackKey(KeyEvent.KEYCODE_A,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)));
        ClipboardNativeSearch1803.end();
        assertFalse(ClipboardNativeSearch1803.isOpen());
    }

    @Test public void searchCannotOpenWhileBindingsAreUnreviewed() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertFalse(ClipboardNativeSearch1803.available());
        assertFalse(ClipboardNativeSearch1803.open(null, new View(activity), List.of()));
        assertFalse(ClipboardNativeSearch1803.isOpen());
    }

    /** Succeeds every step; records calls. Optionally ends the session from inside the switch. */
    static final class FakeHost implements ClipboardNativeSearchSession.Host {
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

}
