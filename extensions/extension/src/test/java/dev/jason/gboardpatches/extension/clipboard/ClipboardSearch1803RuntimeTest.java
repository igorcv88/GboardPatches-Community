package dev.jason.gboardpatches.extension.clipboard;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE)
public class ClipboardSearch1803RuntimeTest {
    public static final class Holder { public final View a; Holder(View a) { this.a=a; } }
    public static final class Recycler extends LinearLayout { public Object l=new Object(); Recycler(Activity a) { super(a); } }
    public static final class Adapter {
        public final List<Object> n = new ArrayList<>();
        public final List<Holder> holders = new ArrayList<>();
        public int o=2, y=1, z=0, notifications;
        public void gz() {
            notifications++;
            for (int i=0;i<holders.size();i++) {
                ClipboardSearch1803Runtime.beforeBind(this, holders.get(i), i);
                ClipboardSearch1803Runtime.afterBind(this, holders.get(i), i);
            }
        }
    }
    public static final class Keyboard { public Adapter c; public View l; }
    @Test public void nativeIndicesModelsCountsAndViewsSurviveSearchRefreshAndClose() throws Exception {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout root=new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL);
        FrameLayout title=new FrameLayout(activity); title.setId(0x7f0b01c9);
        TextView label=new TextView(activity); label.setId(0x7f0b01c8); title.addView(label); root.addView(title);
        LinearLayout actions=new LinearLayout(activity); actions.setId(0x7f0b0527); root.addView(actions);
        FrameLayout body=new FrameLayout(activity); root.addView(body);
        Recycler recycler=new Recycler(activity); body.addView(recycler, new FrameLayout.LayoutParams(-1,-1));
        activity.setContentView(root);
        Class<?> clip=Class.forName("fih");
        Object a=header(clip,"a"), b=header(clip,"b"), c=header(clip,"c");
        java.lang.reflect.Constructor<?> constructor=clip.getDeclaredConstructor(long.class,String.class,boolean.class);
        constructor.setAccessible(true);
        Object first=constructor.newInstance(1,"alpha",false);
        Object secret=constructor.newInstance(2,"needle secret",true);
        Object pinned=constructor.newInstance(3,"needle " + "x".repeat(200000),false);
        Adapter adapter=new Adapter(); adapter.n.addAll(List.of(a, first, secret, b, pinned, c));
        for (int i=0;i<adapter.n.size();i++) {
            View card=new View(activity); card.setLayoutParams(new ViewGroup.MarginLayoutParams(-1,80));
            adapter.holders.add(new Holder(card)); recycler.addView(card);
        }
        List<Object> original=List.copyOf(adapter.n);
        Keyboard keyboard=new Keyboard(); keyboard.c=adapter; keyboard.l=recycler;
        ClipboardSearch1803Runtime.opened(keyboard);
        EditText input=(EditText) title.getChildAt(1); input.setText("needle");
        settle();
        assertEquals(View.GONE,adapter.holders.get(1).a.getVisibility());
        assertEquals(View.GONE,adapter.holders.get(2).a.getVisibility()); // Sensitive text never searched.
        assertEquals(View.VISIBLE,adapter.holders.get(4).a.getVisibility());
        assertEquals(original,adapter.n); assertSame(pinned,adapter.n.get(4));
        assertEquals(2,adapter.o); assertEquals(1,adapter.y);
        adapter.n.remove(pinned); adapter.holders.remove(4);
        ClipboardSearch1803Runtime.changed(adapter); settle();
        assertEquals(5,adapter.n.size()); assertFalse(adapter.n.contains(pinned));
        input.setText(""); settle();
        assertEquals(View.VISIBLE,adapter.holders.get(1).a.getVisibility());
        assertEquals(80,adapter.holders.get(1).a.getLayoutParams().height);
        ClipboardSearch1803Runtime.closed(new Keyboard()); settle();
        assertEquals(2,title.getChildCount()); // Stale owner cannot close the active panel.
        ClipboardSearch1803Runtime.closed(keyboard); settle();
        assertSame(body,recycler.getParent()); assertEquals(1,title.getChildCount());
        assertEquals(0,actions.getChildCount());
    }
    private static Object header(Class<?> type, String name) throws Exception {
        java.lang.reflect.Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(null);
    }
    private static void settle() throws Exception {
        for(int i=0;i<20;i++) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20); }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
}
