package dev.jason.gboardpatches.extension.clipboard;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class ClipboardNative1803Test {
    private static Class<?> stubs() throws Exception { return Class.forName("ClipboardTestNative"); }

    @Test public void backKeyActionIsTheSoftKeysOwnActionAndIsDispatchedThroughTheClipboardManager() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        View key = (View) Class.forName("ClipboardTestNative$Key").getConstructor(android.content.Context.class, int.class)
                .newInstance(activity, -10001);
        key.setId(ClipboardNative1803.BACK_TO_PRIME);
        root.addView(key);
        Object action = ClipboardNative1803.backKeyAction(root);
        assertEquals(Integer.valueOf(-10001), ClipboardNative1803.actionCode(action));

        Object keyboard = Class.forName("ClipboardTestNative$Keyboard").getConstructor().newInstance();
        assertTrue(ClipboardNative1803.dispatch(keyboard, action));
        List<?> events = (List<?>) stubs().getMethod("events", Object.class).invoke(null, keyboard);
        assertEquals(1, events.size());
        Method code = stubs().getMethod("code", Object.class);
        assertEquals(-10001, code.invoke(null, events.get(0)));
    }

    @Test public void returnUsesTheNativeClipboardDescriptorPrimaryAction() throws Exception {
        Object controller = stubs().getMethod("controller").invoke(null);
        Object action = ClipboardNative1803.clipboardAction(controller);
        assertEquals(Integer.valueOf(-10124), ClipboardNative1803.actionCode(action));
        Object keyboard = Class.forName("ClipboardTestNative$Keyboard").getConstructor().newInstance();
        assertTrue(ClipboardNative1803.dispatch(keyboard, action));
        List<?> events = (List<?>) stubs().getMethod("events", Object.class).invoke(null, keyboard);
        assertEquals(1, events.size());
        assertNull(ClipboardNative1803.clipboardAction(new Object()));
    }

    @Test public void missingKeyOrManagerNeverDispatches() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertNull(ClipboardNative1803.backKeyAction(new FrameLayout(activity)));
        assertNull(ClipboardNative1803.backKeyAction(null));
        assertNull(ClipboardNative1803.actionCode(null));
        assertFalse(ClipboardNative1803.dispatch(new Object(), new Object()));
        assertFalse(ClipboardNative1803.dispatch(null, null));
    }

    @Test public void deleteUsesTheNativeBatchAndFailsClosedWhenTheStoreCannotConfirm() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        @SuppressWarnings("unchecked")
        java.lang.reflect.Field field = Class.forName("fjv").getDeclaredField("events");
        field.setAccessible(true);
        List<String> events = (List<String>) field.get(null);
        events.clear();
        // The test store cannot be re-queried, so deletion is not confirmed: no false success.
        assertFalse(ClipboardNative1803.delete(activity, List.of(42L)));
        assertEquals(List.of("delete:42"), events);
    }
}
