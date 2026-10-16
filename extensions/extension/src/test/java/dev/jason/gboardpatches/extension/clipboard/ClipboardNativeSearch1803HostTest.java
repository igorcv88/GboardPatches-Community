package dev.jason.gboardpatches.extension.clipboard;

import android.app.Activity;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
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
        void Z(Provider provider, boolean reset) { providers.add(provider); resets.add(reset); }
    }
    public static class Field extends View implements Provider { public Field(android.content.Context c) { super(c); } }

    @org.junit.Before public void provider() { ClipboardNativeSearch1803.providerInterface = Provider.class.getName(); }
    @org.junit.After public void reset() { ClipboardNativeSearch1803.providerInterface = "oqa"; }

    @Test public void routesToTheFieldWithJarvisResetFlagAndRestoreStaysUnboundUntilReviewed() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Service service = new Service();
        Field field = new Field(activity);
        ClipboardNativeSearch1803.Host host = new ClipboardNativeSearch1803.Host(service, field);
        assertTrue(host.routeToField());
        assertEquals(List.of(field), service.providers);
        assertEquals(List.of(false), service.resets);
        host.restoreRouting();
        assertEquals(ClipboardNativeSearch1803.ROUTING_RESTORE_REVIEWED ? 2 : 1, service.providers.size());
        assertFalse(host.switchToAlphabet());
    }

    @Test public void nonProviderFieldIsNeverRouted() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Service service = new Service();
        ClipboardNativeSearch1803.Host host = new ClipboardNativeSearch1803.Host(service, new View(activity));
        assertFalse(host.routeToField());
        assertTrue(service.providers.isEmpty());
    }

    @Test public void searchCannotOpenWhileBindingsAreUnreviewed() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertFalse(ClipboardNativeSearch1803.open(new View(activity), List.of()));
        assertFalse(ClipboardNativeSearch1803.isOpen());
        ClipboardNativeSearch1803.end();
    }
}
