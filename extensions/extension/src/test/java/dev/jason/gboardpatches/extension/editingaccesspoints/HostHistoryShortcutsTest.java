package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class HostHistoryShortcutsTest {
    @Test public void sendsOneMatchedCtrlZShortcutForUndo() {
        verify(EditorAction.UNDO, KeyEvent.META_CTRL_ON);
    }

    @Test public void sendsOneMatchedCtrlShiftZShortcutForRedo() {
        verify(EditorAction.REDO, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON);
    }

    private static void verify(EditorAction action, int modifiers) {
        List<KeyEvent> events = new ArrayList<>();
        InputConnection editor = (InputConnection) Proxy.newProxyInstance(
                HostHistoryShortcutsTest.class.getClassLoader(), new Class<?>[]{InputConnection.class},
                (target, method, arguments) -> {
                    if ("sendKeyEvent".equals(method.getName())) {
                        events.add((KeyEvent) arguments[0]);
                        return true;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
        assertTrue(HostHistoryShortcuts.send(editor, action));
        assertEquals(2, events.size());
        assertEquals(KeyEvent.ACTION_DOWN, events.get(0).getAction());
        assertEquals(KeyEvent.ACTION_UP, events.get(1).getAction());
        for (KeyEvent event : events) {
            assertEquals(KeyEvent.KEYCODE_Z, event.getKeyCode());
            assertEquals(modifiers, event.getMetaState());
        }
    }

    @Test public void refusesClipboardActionsAndAbsentConnections() {
        assertFalse(HostHistoryShortcuts.send(null, EditorAction.UNDO));
        assertFalse(HostHistoryShortcuts.send(null, EditorAction.COPY));
    }
}
