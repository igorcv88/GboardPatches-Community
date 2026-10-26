package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Reviewed 18.0.3 native entry points used by the composite clipboard search.
 * Every call is reflective and fails closed (false/null); nothing here logs clip text.
 */
final class ClipboardNative1803 {
    /** id/key_pos_clipboard_back_to_prime (res/_f7.xml, res/bAF.xml). */
    static final int BACK_TO_PRIME = 0x7f0b0521;

    private ClipboardNative1803() {}

    /** The back-to-alphabet SoftKeyView's own primary action, or null. */
    static Object backKeyAction(View root) {
        try {
            View key = root == null ? null : root.findViewById(BACK_TO_PRIME);
            return key == null ? null : call(key, "h"); // SoftKeyView.h(): primary action (Lpnu).
        } catch (Throwable ignored) { return null; }
    }

    /** Lpnu.c: the integer code of an action, or null. */
    static Integer actionCode(Object action) {
        try { return action == null ? null : ((Number) field(action, "c")).intValue(); }
        catch (Throwable ignored) { return null; }
    }

    /**
     * Emits the key's own action through the clipboard keyboard's manager:
     * Lnur.d(Lpnu) → ClipboardKeyboard.x (Loxv).M(Lnur). This is the event a tap produces,
     * without inventing a code or a keyboard descriptor.
     */
    static boolean dispatch(Object keyboard, Object action) {
        try {
            if (keyboard == null || action == null) return false;
            ClassLoader loader = keyboard.getClass().getClassLoader();
            Class<?> eventType = Class.forName("nur", false, loader);
            Method factory = eventType.getDeclaredMethod("d", action.getClass());
            factory.setAccessible(true);
            Object event = factory.invoke(null, action);
            Object manager = field(keyboard, "x");
            if (event == null || manager == null) return false;
            Method dispatch = manager.getClass().getMethod("M", eventType);
            dispatch.setAccessible(true);
            dispatch.invoke(manager, event);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    /** Resolve the stock clipboard action from mlh.h, the native descriptor catalog.
     * mic.e(pmy.a) is exactly the primary-click ActionDef used by mln.K. */
    static Object clipboardAction(Object controller) {
        try {
            Object catalog = field(controller, "h");
            Object descriptor;
            if (catalog instanceof java.util.Map<?, ?> entries) descriptor = entries.get("clipboard");
            else {
                Method get = catalog.getClass().getMethod("get", Object.class); get.setAccessible(true);
                descriptor = get.invoke(catalog, "clipboard");
            }
            if (descriptor == null || !"clipboard".equals(field(descriptor, "b"))) return null;
            Class<?> click = Class.forName("pmy", false, controller.getClass().getClassLoader());
            java.lang.reflect.Field primary = click.getDeclaredField("a"); primary.setAccessible(true);
            Method action = descriptor.getClass().getDeclaredMethod("e", click); action.setAccessible(true);
            Object definition = action.invoke(descriptor, primary.get(null));
            return definition == null ? null : call(definition, "b");
        } catch (Throwable ignored) { return null; }
    }

    /**
     * Persistent delete of exactly these ids: Lfjo.h(Context, List<Long>) builds one
     * ContentProviderOperation.newDelete per item URI (Lfjo.d(ctx, 1, id)) and applies the batch.
     * Lfjo.h swallows failures, so success is proven by re-querying each item URI.
     * Must run off the main thread.
     */
    static boolean delete(Context context, List<Long> ids) {
        try {
            ClassLoader loader = context.getClassLoader();
            Class<?> store = Class.forName("fjo", false, loader);
            Method h = store.getDeclaredMethod("h", Context.class, List.class);
            h.setAccessible(true);
            h.invoke(null, context, ids);
            Method d = store.getDeclaredMethod("d", Context.class, int.class, long.class);
            d.setAccessible(true);
            for (long id : ids) {
                Uri item = (Uri) d.invoke(null, context, 1, id);
                if (item == null) return false;
                try (Cursor cursor = context.getContentResolver().query(item, null, null, null, null)) {
                    if (cursor == null || cursor.getCount() != 0) return false;
                }
            }
            return true;
        } catch (Throwable ignored) { return false; }
    }

    static Object field(Object receiver, String name) throws Exception {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(receiver); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    static Object call(Object receiver, String name) throws Exception {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            try { Method method = type.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(receiver); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
}
