package dev.jason.gboardpatches.extension.clipboard;

import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/** Temporary opt-in geometry/event probe. Never pass query, clip text, IDs or key events. */
final class ClipboardSearchDiagnostics {
    private static final String TAG = "ClipboardSearchTouch";
    private ClipboardSearchDiagnostics() { }
    static void state(String event) {
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, event);
    }
    static void touch(String target, MotionEvent event) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
            Log.d(TAG, target + " action=" + action + " x=" + event.getX() + " y=" + event.getY());
    }
    static void anchor(View view) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return;
        int[] at = new int[2]; view.getLocationInWindow(at);
        Log.d(TAG, "anchored panel=" + new Rect(at[0], at[1], at[0] + view.getWidth(), at[1] + view.getHeight()));
    }
    static void insets(String stage, InputMethodService.Insets value, Rect panel) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return;
        Log.d(TAG, stage + " mode=" + value.touchableInsets + " content=" + value.contentTopInsets
                + " visible=" + value.visibleTopInsets + " region=" + value.touchableRegion
                + " panel=" + panel);
    }
}
