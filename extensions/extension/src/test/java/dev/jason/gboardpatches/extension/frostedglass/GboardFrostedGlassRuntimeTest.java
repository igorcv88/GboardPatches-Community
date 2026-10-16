package dev.jason.gboardpatches.extension.frostedglass;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.Window;
import android.widget.FrameLayout;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,manifest=Config.NONE,shadows={GboardFrostedGlassRuntimeTest.BlurWindow.class,GboardFrostedGlassRuntimeTest.BlurManager.class})
public class GboardFrostedGlassRuntimeTest {
    @Implements(className="android.view.WindowManagerImpl",isInAndroidSdk=false)
    public static class BlurManager extends org.robolectric.shadows.ShadowWindowManagerImpl {
        @Implementation protected boolean isCrossWindowBlurEnabled() { return true; }
    }
    @Implements(className="com.android.internal.policy.PhoneWindow",isInAndroidSdk=false)
    public static class BlurWindow extends org.robolectric.shadows.ShadowPhoneWindow {
        @RealObject Window window;
        int blurWrites,clearWrites,backgroundWrites;
        @Implementation public void setBackgroundBlurRadius(int radius) {
            if(radius==0)clearWrites++;else blurWrites++;
        }
        @Implementation public void setBackgroundDrawable(Drawable background) {
            backgroundWrites++; window.getDecorView().setBackground(background);
        }
    }
    @Test public void repeatedSchedulesCoalesceAndUnchangedBlurDoesNotClearOrRebuild() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        GboardPatchesSettings.preferences(activity).edit().putBoolean(GboardFrostedGlassSettings.PREF_KEY_ENABLED,true).apply();
        Window window=activity.getWindow();
        FrameLayout keyboard=new FrameLayout(activity); keyboard.setTag(".keyboard-base-area.keyboard-outline");
        activity.setContentView(keyboard);
        window.getDecorView().layout(0,0,320,160);
        BlurWindow shadow=org.robolectric.shadow.api.Shadow.extract(window);
        shadow.blurWrites=shadow.clearWrites=shadow.backgroundWrites=0;
        for(int i=0;i<100;i++)GboardFrostedGlassRuntime.afterConfigureWindow(window,false,false);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
        assertEquals(1,shadow.blurWrites); assertEquals(0,shadow.clearWrites);
        int backgrounds=shadow.backgroundWrites;
        for(int i=0;i<100;i++)GboardFrostedGlassRuntime.afterConfigureWindow(window,false,false);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
        assertEquals(1,shadow.blurWrites); assertEquals(0,shadow.clearWrites);
        assertEquals(backgrounds,shadow.backgroundWrites);
        GboardFrostedGlassRuntime.afterConfigureWindow(window,true,false);
        assertEquals(1,shadow.clearWrites);
    }
    @Test public void disablingCancelsPendingWorkAndRestoresWindowBackground() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        GboardPatchesSettings.preferences(activity).edit().putBoolean(GboardFrostedGlassSettings.PREF_KEY_ENABLED,true).apply();
        Window window=activity.getWindow(); Drawable original=window.getDecorView().getBackground();
        GboardFrostedGlassRuntime.afterConfigureWindow(window,false,false);
        GboardFrostedGlassRuntime.afterConfigureWindow(window,true,false);
        BlurWindow shadow=org.robolectric.shadow.api.Shadow.extract(window); shadow.blurWrites=0;
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        assertEquals(0,shadow.blurWrites); assertSame(original,window.getDecorView().getBackground());
    }
}
