package dev.jason.gboardpatches.extension.frostedglass;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public final class GboardFrostedGlassLifecycleRuntimeTest {
    @Test
    public void belowAndroid12StockLifecycleIsUnaffected() {
        GboardFrostedGlassLifecycleRuntime.afterConfigureWindow(null, false, false);
        GboardFrostedGlassLifecycleRuntime.onInputViewStarted(new Object());
        GboardFrostedGlassLifecycleRuntime.onInputWindowHidden(new Object());
    }

    @Test
    @Config(sdk = 33)
    public void unexpectedReceiversDoNotEscapeTheLifecycleBoundary() {
        GboardFrostedGlassLifecycleRuntime.afterConfigureWindow(null, true, true);
        GboardFrostedGlassLifecycleRuntime.onInputViewStarted("not an input method service");
        GboardFrostedGlassLifecycleRuntime.onInputWindowHidden("not an input method service");
    }
}
