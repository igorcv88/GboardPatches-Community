package dev.jason.gboardpatches.extension.quicktoolbar;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class QuickToolbarBindingTest {
    static final class Fixture {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity), row = new FrameLayout(activity), keys = new FrameLayout(activity);
        FrameLayout button = new FrameLayout(activity), content = new FrameLayout(activity);
        View candidates = new View(activity), favorites = new View(activity);
        ColorDrawable stock = new ColorDrawable(0xff123456);
        int stockClicks, showCalls, hideCalls;
        boolean showWorks = true;
        QuickToolbarBinding binding;
        Fixture() {
            root.addView(row); root.addView(keys);row.addView(button);button.addView(content);
            row.addView(candidates);row.addView(favorites);favorites.setVisibility(View.GONE);
            button.setForeground(stock);button.setContentDescription("Stock menu");
            button.setOnClickListener(v -> stockClicks++);
            activity.setContentView(root);
            binding = new QuickToolbarBinding(button, content, 0xffaaaaaa, new QuickToolbarBinding.Favorites() {
                public boolean show() {
                    showCalls++;
                    if (showWorks) {favorites.setVisibility(View.VISIBLE);candidates.setVisibility(View.INVISIBLE);}
                    return showWorks;
                }
                public void hide() {
                    hideCalls++;favorites.setVisibility(View.GONE);candidates.setVisibility(View.VISIBLE);
                }
            });
        }
    }
    @Test public void sameRowToggleLeavesKeysAndStockListenerIntact() {
        Fixture f = new Fixture();
        f.binding.update(true,true,true);
        assertNotSame(f.stock,f.button.getForeground());assertEquals(View.INVISIBLE,f.content.getVisibility());
        assertTrue(f.binding.onMenuEvent());assertEquals(1,f.showCalls);
        assertSame(f.row,f.favorites.getParent());assertSame(f.root,f.keys.getParent());
        assertEquals(View.VISIBLE,f.keys.getVisibility());assertEquals(View.VISIBLE,f.favorites.getVisibility());
        assertTrue(f.binding.onMenuEvent());assertEquals(View.VISIBLE,f.candidates.getVisibility());
        assertEquals(View.GONE,f.favorites.getVisibility());assertEquals(0,f.stockClicks);
        f.binding.update(true,true,false);
        assertFalse(f.binding.onMenuEvent());assertSame(f.stock,f.button.getForeground());
        assertEquals(View.VISIBLE,f.content.getVisibility());assertEquals("Stock menu",f.button.getContentDescription());
        f.button.performClick();assertEquals(1,f.stockClicks);
    }
    @Test public void lifecycleAndDisabledStateRestoreTheOriginalView() {
        for(int mode=0;mode<3;mode++) {
            Fixture f=new Fixture();f.binding.update(true,true,true);f.binding.onMenuEvent();
            if(mode==0)f.binding.close();
            else f.binding.update(mode!=1,mode!=2,true);
            assertSame(f.stock,f.button.getForeground());assertEquals(View.VISIBLE,f.content.getVisibility());
            assertEquals(View.GONE,f.favorites.getVisibility());assertFalse(f.binding.onMenuEvent());
        }
    }
    @Test public void rejectedFavoritesRequestDoesNotOpenTheAllShortcutsMenu() {
        Fixture f=new Fixture();f.showWorks=false;f.binding.update(true,true,true);
        assertTrue(f.binding.onMenuEvent());assertEquals(0,f.stockClicks);
        assertEquals(QuickToolbarState.Mode.SUGGESTIONS,f.binding.mode());
        assertEquals(View.VISIBLE,f.candidates.getVisibility());
    }
    @Test public void arrowRefreshesOnLayoutDirectionChangeWithoutModeChange() throws Exception {
        Fixture f=new Fixture();
        f.button.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        f.binding.update(true,true,true);
        Object arrow=f.button.getForeground();
        java.lang.reflect.Field back=arrow.getClass().getDeclaredField("back");
        back.setAccessible(true);
        assertFalse(back.getBoolean(arrow));
        f.button.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        f.binding.update(true,true,true);
        assertSame(arrow,f.button.getForeground());
        assertTrue(back.getBoolean(arrow));
        f.button.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        f.binding.update(true,true,true);
        assertFalse(back.getBoolean(arrow));
    }

}
