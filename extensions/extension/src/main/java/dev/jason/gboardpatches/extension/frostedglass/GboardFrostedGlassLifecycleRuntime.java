package dev.jason.gboardpatches.extension.frostedglass;

import android.os.Build;
import android.view.Window;

/** Thin lifecycle adapter; Gboard version bindings stay in the patch module. */
public final class GboardFrostedGlassLifecycleRuntime {
    private GboardFrostedGlassLifecycleRuntime() {}

    public static void afterConfigureWindow(Window window, boolean fullscreen, boolean candidatesOnly) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            GboardFrostedGlassRuntime.afterConfigureWindow(window, fullscreen, candidatesOnly);
        } catch (Throwable ignored) {
            // Leave the stock IME lifecycle in effect.
        }
    }
    public static void onInputViewStarted(Object receiver) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            GboardFrostedGlassRuntime.onWindowShown(receiver);
        } catch (Throwable ignored) {
            // Leave the stock IME lifecycle in effect.
        }
    }
    public static void onInputWindowHidden(Object receiver) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            GboardFrostedGlassRuntime.onWindowHidden(receiver);
        } catch (Throwable ignored) {
            // Leave the stock IME lifecycle in effect.
        }
    }
}
