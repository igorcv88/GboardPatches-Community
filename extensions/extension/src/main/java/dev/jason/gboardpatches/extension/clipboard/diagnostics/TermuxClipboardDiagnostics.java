package dev.jason.gboardpatches.extension.clipboard;

import android.text.InputType;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;

/** Read-only observation of Lfif. No clipboard access, text access, or terminal operations. */
public final class TermuxClipboardDiagnostics {
    private static final String TAG = "GboardClipTrace";
    private static final AtomicIntegerArray LAST = new AtomicIntegerArray(new int[]{-1, -1, -1});
    private TermuxClipboardDiagnostics() { }

    public static void prepared(Object helper) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return;
        for (int i = 0; i < LAST.length(); i++) LAST.set(i, -1);
        observe(helper, 0);
    }
    public static void refreshed(Object helper) { observe(helper, 1); }
    public static void requested(Object helper) { observe(helper, 2); }

    private static void observe(Object helper, int stage) {
        // Opt in with log.tag.GboardClipTrace=DEBUG. Normal sessions pay only this check.
        if (!Log.isLoggable(TAG, Log.DEBUG)) return;
        int state = snapshot(helper);
        if (state < 0 || LAST.getAndSet(stage, state) == state) return;
        Log.d(TAG, "stage=" + stage + " state=" + state);
    }

    /** Bitset only: never inspects a clip, surrounding text, field label, or package alias. */
    static int snapshot(Object helper) {
        try {
            Object value = field(helper, "m");
            if (!(value instanceof EditorInfo info) || !"com.termux".equals(info.packageName)) return -1;
            int type = info.inputType;
            boolean terminal = type == InputType.TYPE_NULL ||
                    (type & (InputType.TYPE_MASK_CLASS | InputType.TYPE_MASK_VARIATION)) ==
                            (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                            && (type & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0;
            if (!terminal) return -1;
            int state = type == InputType.TYPE_NULL ? 1 : 2;
            if ((info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) state |= 4;
            Object source = field(helper, "k");
            if (source instanceof List<?> clips && !clips.isEmpty()) state |= 8;
            if (field(helper, "i") != null) state |= 16;
            if (field(helper, "j") != null) state |= 32;
            if (Boolean.TRUE.equals(field(helper, "q"))) state |= 64;
            return state;
        } catch (Throwable ignored) { return -1; }
    }

    private static Object field(Object receiver, String name) throws ReflectiveOperationException {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(receiver);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
