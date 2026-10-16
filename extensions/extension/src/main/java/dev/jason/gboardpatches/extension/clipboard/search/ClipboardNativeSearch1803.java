package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.graphics.Region;
import android.inputmethodservice.InputMethodService;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputBinding;
import android.view.inputmethod.InputConnection;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 18.0.3 host for {@link ClipboardNativeSearchSession}.
 *
 * <p>Surface: the framework candidates area of the IME window
 * ({@link InputMethodService#setCandidatesView}), above Gboard's own input view; Loup
 * declares {@code onCreateCandidatesView()} returning null and the APK has no static
 * {@code setCandidatesView*} caller, so the area is otherwise unused.
 * Field: Gboard's {@code EditTextOnKeyboard} (implements {@code Loqa}).
 * Routing: {@code Loup.Z(Loqa;Z)V} → {@code Loup.aC(provider, reset, true)} (0x2eeddc).
 * Restore: {@code Z(null, false)} ≡ {@code aC(null, false, true)}, the call Loup makes itself in
 * {@code onFinishInputView} (0x2ee044, pc≈128–130).
 * Touch: Loup declares its own {@code onComputeInsets} (0x2ed414); {@link #afterComputeInsets}
 * adds only the surface rectangle on top of the native result, and only while it is shown.
 *
 * <p>Fails closed: {@link #available} stays false until every flag below is confirmed on the
 * 18.0.3 binary and on device. Until then the search button is not shown at all.
 */
public final class ClipboardNativeSearch1803 {
    static final String FIELD_CLASS = "com.google.android.libraries.inputmethod.widgets.EditTextOnKeyboard";
    static String providerInterface = "oqa"; // Loqa; replaceable only by tests.

    /** Each flag flips to true only with binary and device evidence (clipboard-native-search-plan). */
    static final boolean ROUTING_RESTORE_REVIEWED = false;     // T2: Loqe.n(null) / Loup.C branch
    static final boolean ALPHABET_SWITCH_REVIEWED = false;     // T1: clipboard body -> alphabet
    static final boolean RETURN_TO_CLIPBOARD_REVIEWED = false; // T1: alphabet -> clipboard panel
    static final boolean INSETS_REVIEWED = false;              // T3: device measurement

    private static ClipboardNativeSearchSession active;
    private static ClipboardSearchSurface surface;
    private static boolean opening, endRequested;
    private static InputMethodService.Insets computing;
    private ClipboardNativeSearch1803() {}

    public static boolean available() {
        return ROUTING_RESTORE_REVIEWED && ALPHABET_SWITCH_REVIEWED
                && RETURN_TO_CLIPBOARD_REVIEWED && INSETS_REVIEWED;
    }

    /** Called from the clipboard header search button with the current text snapshot. */
    public static boolean open(View anchor, List<ClipboardSearchSurface.Clip> clips) {
        if (!main() || !available() || active != null) return false;
        InputMethodService ime = service(anchor.getContext());
        if (ime == null) return false;
        try {
            Object field = Class.forName(FIELD_CLASS, true, ime.getClass().getClassLoader())
                    .getConstructor(Context.class).newInstance(ime);
            return start(ime, (View) field, clips);
        } catch (Throwable failure) {
            report("open", failure);
            return false;
        }
    }

    static boolean start(InputMethodService ime, View field, List<ClipboardSearchSurface.Clip> clips) {
        return start(ime, field, clips, new Host(ime, field));
    }

    static boolean start(InputMethodService ime, View field, List<ClipboardSearchSurface.Clip> clips,
            ClipboardNativeSearchSession.Host host) {
        ClipboardNativeSearchSession session = new ClipboardNativeSearchSession(host);
        int height = Math.round(ime.getResources().getDisplayMetrics().heightPixels * 0.38f);
        surface = new ClipboardSearchSurface(ime, field, height,
                ClipboardSearchController.forImeMainThread(), new ClipboardSearchSurface.Listener() {
                    public void onPick(ClipboardSearchSurface.Clip clip) { session.pick(clip.text); finish(); }
                    public void onClose() { session.cancel(); finish(); }
                });
        surface.setClips(clips);
        active = session;
        // Switching keyboards may run Loup lifecycle callbacks; they must not tear down a
        // half-open session. A request seen meanwhile is honoured right after opening.
        opening = true; endRequested = false;
        boolean opened;
        try { opened = session.open(); }
        catch (Throwable failure) { report("start", failure); opened = false; }
        finally { opening = false; }
        if (!opened) { finish(); return false; }
        if (endRequested) { end(); return false; }
        ClipboardSearchSurface shown = surface;
        if (shown == null) return false;
        // Attachment alone is not visibility: after layout the surface must have real size.
        shown.postDelayed(() -> {
            if (surface == shown && (!shown.isShown() || shown.getHeight() == 0)) {
                report("surface", new IllegalStateException("not visible"));
                end();
            }
        }, 500);
        return true;
    }

    /** Input session ended or the window hid: restore provider and surface, keep the keyboard. */
    public static void end() {
        if (!main()) return;
        if (opening) { endRequested = true; return; }
        ClipboardNativeSearchSession session = active;
        if (session == null) return;
        session.end();
        finish();
    }

    /**
     * Loup.onKeyDown/onKeyUp entry (0x4db8a0 / 0x4dbb1c). While searching, the first Back
     * closes only the search and returns to the clipboard; otherwise the native path runs.
     */
    public static boolean onBackKey(int keyCode, KeyEvent event) {
        if (!main() || keyCode != KeyEvent.KEYCODE_BACK || !isOpen() || event == null) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            ClipboardNativeSearchSession session = active;
            session.cancel();
            finish();
        }
        return true;
    }

    /**
     * Loup.onComputeInsets entry/exit. Keeps Gboard's own result and only adds the shown surface:
     * touchable region union in REGION mode, otherwise the visible top moves up to the surface.
     * Content insets are not changed, so the editor is not resized behind the surface.
     */
    public static void beforeComputeInsets(Object value) {
        if (main()) computing = value instanceof InputMethodService.Insets insets ? insets : null;
    }

    public static void afterComputeInsets() {
        InputMethodService.Insets insets = computing;
        computing = null;
        try {
            if (!main() || !isOpen() || insets == null) return;
            ClipboardSearchSurface current = surface;
            if (current == null || !current.isShown() || current.getHeight() == 0) return;
            int[] location = new int[2];
            current.getLocationInWindow(location);
            Rect rect = new Rect(location[0], location[1],
                    location[0] + current.getWidth(), location[1] + current.getHeight());
            apply(insets, rect);
        } catch (Throwable failure) { report("insets", failure); }
    }

    static void apply(InputMethodService.Insets insets, Rect surfaceRect) {
        if (insets.touchableInsets == InputMethodService.Insets.TOUCHABLE_INSETS_REGION) {
            insets.touchableRegion.op(surfaceRect, Region.Op.UNION);
        }
        insets.visibleTopInsets = Math.min(insets.visibleTopInsets, surfaceRect.top);
    }

    static boolean isOpen() { return active != null && active.state() == ClipboardNativeSearchSession.State.OPEN; }

    private static void finish() {
        active = null;
        ClipboardSearchSurface current = surface;
        surface = null;
        if (current != null) current.close();
    }

    static final class Host implements ClipboardNativeSearchSession.Host {
        private final InputMethodService ime;
        private final View field;
        Host(InputMethodService ime, View field) { this.ime = ime; this.field = field; }

        @Override public boolean showSurface() {
            if (surface == null) return false;
            ime.setCandidatesView(surface);
            ime.setCandidatesViewShown(true);
            // Real visibility is verified after layout in start().
            return surface.getParent() != null;
        }

        @Override public void hideSurface() {
            ime.setCandidatesViewShown(false);
            // An empty placeholder: the candidates frame keeps no reference to the surface.
            ime.setCandidatesView(new View(ime));
        }

        @Override public boolean switchToAlphabet() {
            return false; // Binding pending (ALPHABET_SWITCH_REVIEWED): see plan, T1.
        }

        /** Mirrors JarvisPromptKeyboard.K: focus, obtain the field connection, then route. */
        @Override public boolean routeToField() {
            if (!field.isAttachedToWindow() || !field.requestFocus()) return false;
            try {
                Method connection = field.getClass().getMethod("getCurrentInputConnection");
                if (connection.invoke(field) == null) return false;
            } catch (Throwable failure) {
                report("field", failure);
                return false;
            }
            return setProvider(field);
        }

        /** Z(null, false) is aC(null, false, true): Loup's own onFinishInputView cleanup. */
        @Override public void restoreRouting() {
            setProvider(null);
            field.clearFocus();
        }

        @Override public void returnToClipboard() {
            // Binding pending (RETURN_TO_CLIPBOARD_REVIEWED): see plan, T1.
        }

        @Override public boolean commitToEditor(String text) {
            InputConnection current = ime.getCurrentInputConnection();
            if (current == null || dummy(current)) return false;
            return current.commitText(text, 1);
        }

        /** Same test as Loup.az(): the binding's own connection means no editor is bound. */
        private boolean dummy(InputConnection current) {
            InputBinding binding = ime.getCurrentInputBinding();
            InputConnection raw = dev.jason.gboardpatches.extension.editingaccesspoints
                    .JournalingInputConnection.unwrap(current);
            return binding == null || raw == binding.getConnection();
        }

        private boolean setProvider(Object provider) {
            try {
                Class<?> loqa = Class.forName(providerInterface, false, ime.getClass().getClassLoader());
                if (provider != null && !loqa.isInstance(provider)) return false;
                Method z = find(ime.getClass(), loqa);
                if (z == null) return false;
                z.invoke(ime, provider, false);
                return true;
            } catch (Throwable failure) {
                report("route", failure);
                return false;
            }
        }

        private static Method find(Class<?> type, Class<?> loqa) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try {
                    Method method = c.getDeclaredMethod("Z", loqa, boolean.class);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) { }
            }
            return null;
        }
    }

    static InputMethodService service(Context context) {
        for (Context c = context; c != null; c = c instanceof ContextWrapper w ? w.getBaseContext() : null) {
            if (c instanceof InputMethodService ime) return ime;
        }
        return null;
    }

    private static boolean main() { return Looper.myLooper() == Looper.getMainLooper(); }

    private static void report(String operation, Throwable failure) {
        android.util.Log.w("GboardPatches", "Clipboard native search: " + operation
                + " (" + failure.getClass().getSimpleName() + ")");
    }
}
