package dev.jason.gboardpatches.extension.clipboard;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE)
public class ClipboardSearch1803RuntimeTest {
    public static final class Adapter { public final List<Object> n = new ArrayList<>(); }
    public static final class Keyboard { public Adapter c; public View l; }

    private static Object header(Class<?> type, String name) throws Exception {
        java.lang.reflect.Field field=type.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }

    @Test public void snapshotKeepsNativeOrderAndSectionsAndNeverCopiesSensitiveText() throws Exception {
        Class<?> clip=Class.forName("fih");
        Object a=header(clip,"a"), b=header(clip,"b"), c=header(clip,"c");
        java.lang.reflect.Constructor<?> constructor=clip.getDeclaredConstructor(long.class,String.class,boolean.class);
        constructor.setAccessible(true);
        Object first=constructor.newInstance(1L,"alpha",false);
        Object secret=constructor.newInstance(2L,"needle secret",true);
        Object empty=constructor.newInstance(4L,"",false);
        Object pinned=constructor.newInstance(3L,"needle " + "x".repeat(200000),false);
        Adapter adapter=new Adapter(); adapter.n.addAll(List.of(a, first, secret, empty, b, pinned, c));
        List<Object> original=List.copyOf(adapter.n);
        List<ClipboardSearchSurface.Clip> clips=ClipboardSearch1803Runtime.snapshot(adapter);
        assertEquals(2, clips.size());
        assertEquals(1L, clips.get(0).id);
        assertEquals(ClipboardSearchController.Section.RECENT, clips.get(0).section);
        assertEquals(3L, clips.get(1).id);
        assertEquals(ClipboardSearchController.Section.PINNED, clips.get(1).section);
        assertEquals(200007, clips.get(1).text.length()); // Full text, not a preview.
        for (ClipboardSearchSurface.Clip value : clips) assertFalse(value.text.contains("secret"));
        assertEquals(original, adapter.n);
    }

    @Test public void searchButtonIsAttachedAndRemovedWithTheNativePanel() {
        assertTrue(ClipboardNativeSearch1803.available());
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout root=new LinearLayout(activity);
        LinearLayout actions=new LinearLayout(activity); actions.setId(0x7f0b0527); root.addView(actions);
        FrameLayout body=new FrameLayout(activity); root.addView(body);
        View recycler=new View(activity); body.addView(recycler);
        activity.setContentView(root);
        Keyboard keyboard=new Keyboard(); keyboard.c=new Adapter(); keyboard.l=recycler;
        ClipboardSearch1803Runtime.opened(keyboard);
        assertEquals(1, actions.getChildCount());
        assertEquals("Search clipboard", actions.getChildAt(0).getContentDescription());
        ClipboardSearch1803Runtime.closed(keyboard);
        assertEquals(0, actions.getChildCount());
        assertSame(body, recycler.getParent());
    }
}
