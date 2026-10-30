package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;

/**
 * Host editor's standard keyboard history commands. Android's context-menu IPC only
 * standardizes select/copy/cut/paste; it does not guarantee undo/redo. Keep Gboard's
 * verified journal/transaction as the first choice, using keyboard history only when
 * the custom action otherwise has no applicable local edit.
 */
final class HostHistoryShortcuts {
    private HostHistoryShortcuts() {}

    static boolean send(InputConnection connection, EditorAction action) {
        if (connection == null || (action != EditorAction.UNDO && action != EditorAction.REDO))
            return false;
        int modifiers = KeyEvent.META_CTRL_ON
                | (action == EditorAction.REDO ? KeyEvent.META_SHIFT_ON : 0);
        long now = SystemClock.uptimeMillis();
        KeyEvent down = event(now, now, KeyEvent.ACTION_DOWN, modifiers);
        KeyEvent up = event(now, now + 1, KeyEvent.ACTION_UP, modifiers);
        // Both events belong to one shortcut; do not send a second alternative (Ctrl+Y),
        // which could cause duplicate redo in editors supporting both combinations.
        boolean started = connection.sendKeyEvent(down);
        boolean finished = connection.sendKeyEvent(up);
        return started && finished;
    }

    private static KeyEvent event(long downTime, long eventTime, int action, int modifiers) {
        return new KeyEvent(downTime, eventTime, action, KeyEvent.KEYCODE_Z, 0,
                modifiers, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD);
    }
}
