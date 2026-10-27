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
 * declares {@code onCreateCandidatesView()} returning null. Its INVISIBLE framework
 * ancestor must be shown during search and restored afterward. The slot owns a bounded
 * surface and restores the original children; it never leaves a measuring placeholder.
 * Field: Gboard's {@code EditTextOnKeyboard} (implements {@code Loqa}).
 * Routing: {@code Loup.Z(Loqa;Z)V} → {@code Loup.aC(provider, reset, true)} (0x2eeddc).
 * Restore: {@code Z(null, false)} ≡ {@code aC(null, false, true)}, the call Loup makes itself in
 * {@code onFinishInputView} (0x2ee044, pc≈128–130).
 * Touch: Loup declares its own {@code onComputeInsets} (0x2ed414); {@link #afterComputeInsets}
 * adds only the surface rectangle on top of the native result, and only while it is shown.
 *
 * <p>Search is enabled for the reviewed binary. Missing native actions, detached input fields
 * or failed provider confirmation abort opening and restore the editor connection.
 */
public final class ClipboardNativeSearch1803 {
    static final String FIELD_CLASS = "com.google.android.libraries.inputmethod.widgets.EditTextOnKeyboard";
    static String providerInterface = "oqa"; // Loqa; replaceable only by tests.

    /** Native routing, action factories and catalog were checked against the exact 18.0.3 APK.
     * Device layout remains an acceptance test, not a compile-time switch that hides search. */
    static final boolean DELETE_REVIEWED = false;
    private static java.lang.ref.WeakReference<Object> controller = new java.lang.ref.WeakReference<>(null);
    public static void controllerPrepared(Object value) {
        if (!main() || value == null) return;
        // The entry hook owns an mlh, not M's overwritten return register (mln).
        // Keep the last valid controller if an incompatible caller is ever observed.
        try {
            Object catalog = ClipboardNative1803.field(value, "h");
            // ays is SimpleArrayMap, not java.util.Map. Check its reviewed get ABI.
            if (catalog != null) {
                catalog.getClass().getMethod("get", Object.class);
                controller = new java.lang.ref.WeakReference<>(value);
            }
        } catch (Throwable ignored) { }

    }

    private static java.lang.ref.WeakReference<InputMethodService> inputService = new java.lang.ref.WeakReference<>(null);
    public static void inputStarted(Object value) {
        if (!main()) return;
        end(); inputService = new java.lang.ref.WeakReference<>(value instanceof InputMethodService ime ? ime : null);
    }

    static Object returnAction() { return ClipboardNative1803.clipboardAction(controller.get()); }

    private static ClipboardNativeSearchSession active;
    private static ClipboardSearchSurface surface;
    private static boolean opening, endRequested;
    private static InputMethodService.Insets computing;
    private ClipboardNativeSearch1803() {}

    public static boolean available() {
        return true;
    }

    /** Called from the clipboard header search button with the current text snapshot. */
    public static boolean open(Object keyboard, View anchor, List<ClipboardSearchSurface.Clip> clips) {
        if (!main() || !available() || active != null) return false;
        reportState("open-requested");
        InputMethodService ime = service(anchor.getContext());
        if (ime == null) { reportState("missing-service"); return false; }
        try {
            Object field = Class.forName(FIELD_CLASS, true, ime.getClass().getClassLoader())
                    .getConstructor(Context.class).newInstance(anchor.getContext());
            if (field instanceof android.widget.EditText text) {
                text.setSingleLine(true);
                text.setHint("Search clipboard");
                text.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                        | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                        | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);
            }
            Object returnAction = returnAction();
            Object alphabetAction = ClipboardNative1803.backKeyAction(anchor.getRootView());
            if (returnAction == null || alphabetAction == null) {
                reportState(returnAction == null ? "missing-return-action" : "missing-alphabet-action"); return false;
            }
            Host host = new Host(ime, (View) field, keyboard, anchor.getRootView());
            host.returnAction = returnAction;
            host.alphabetAction = alphabetAction;
            return start(ime, (View) field, clips, host);
        } catch (Throwable failure) {
            report("open", failure);
            return false;
        }
    }

    static boolean start(InputMethodService ime, View field, List<ClipboardSearchSurface.Clip> clips,
            ClipboardNativeSearchSession.Host host) {
        ClipboardNativeSearchSession session = new ClipboardNativeSearchSession(host);
        int height = Math.min(Math.round(ime.getResources().getDisplayMetrics().heightPixels * 0.30f),
                Math.round(240 * ime.getResources().getDisplayMetrics().density));
        surface = new ClipboardSearchSurface(field.getContext(), field, height,
                ClipboardSearchController.forImeMainThread(), new ClipboardSearchSurface.Listener() {
                    public void onPick(ClipboardSearchSurface.Clip clip) { session.pick(clip.text); finish(); }
                    public void onClose() { session.cancel(); finish(); }
                    public void onDelete(ClipboardSearchSurface.Clip clip) { delete(ime, clip); }
                });
        surface.setDeleteEnabled(DELETE_REVIEWED);
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
                reportState("surface-not-visible; rolling-back");
                // This is a failed open, not a window-close lifecycle event.
                // Restore the complete slot and the native clipboard, not only routing.
                session.cancel();
                finish();
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

    /** Off the IME thread; the row disappears only after the store proves the item is gone. */
    private static void delete(InputMethodService ime, ClipboardSearchSurface.Clip clip) {
        ClipboardSearchSurface target = surface;
        if (!DELETE_REVIEWED || target == null) return;
        Thread worker = new Thread(() -> {
            boolean deleted = ClipboardNative1803.delete(ime, List.of(clip.id));
            target.post(() -> {
                if (surface != target) return;
                if (deleted) target.remove(clip.id);
                else report("delete", new IllegalStateException("not confirmed"));
            });
        }, "gboard-clipboard-delete");
        worker.setDaemon(true);
        worker.start();
    }

    public static boolean isOpen() { return active != null && active.state() == ClipboardNativeSearchSession.State.OPEN; }

    private static void finish() {
        active = null;
        ClipboardSearchSurface current = surface;
        surface = null;
        if (current != null) current.close();
    }

    static final class Host implements ClipboardNativeSearchSession.Host {
        private final InputMethodService ime;
        private final View field;
        private final Object keyboard;
        private final View panelRoot;
        private Object returnAction, alphabetAction;
        private ClipboardSearchWindowSlot slot;
        Host(InputMethodService ime, View field) { this(ime, field, null, null); }
        Host(InputMethodService ime, View field, Object keyboard, View panelRoot) {
            this.ime = ime; this.field = field; this.keyboard = keyboard; this.panelRoot = panelRoot;
        }

        @Override public boolean showSurface() {
            if (surface == null) return false;
            slot = ClipboardSearchWindowSlot.resolve(ime);
            if (slot == null) return false;
            try {
                int height = surface.getLayoutParams().height;
                slot.attach(surface, height);
                ime.setCandidatesViewShown(true);
                return surface.getParent() != null && surface.isShown();
            } catch (Throwable failure) {
                hideSurface();
                report("attach", failure);
                return false;
            }
        }

        @Override public void hideSurface() {
            ClipboardSearchWindowSlot previous = slot;
            slot = null;
            if (previous == null) return;
            try { ime.setCandidatesViewShown(previous.wasShown()); }
            finally { previous.close(); }
        }

        /**
         * Emits the back-to-prime key's own action (0x7f0b0521) through ClipboardKeyboard.x.
         * Uses the same pnu/nur factory and dispatcher as the native access point.
         */
        @Override public boolean switchToAlphabet() {
            return ClipboardNative1803.dispatch(keyboard, alphabetAction != null
                    ? alphabetAction : ClipboardNative1803.backKeyAction(panelRoot));
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
            return setProvider(field) && Boolean.FALSE.equals(editorRouting());
        }

        /** Z(null, false) is aC(null, false, true): Loup's own onFinishInputView cleanup. */
        @Override public boolean restoreRouting() {
            boolean restored = setProvider(null) && Boolean.TRUE.equals(editorRouting());
            field.clearFocus();
            return restored;
        }

        /** Loup.aA(): active Loqe v equals the editor provider r (18.0.3). */
        private Boolean editorRouting() {
            try {
                for (Class<?> type = ime.getClass(); type != null; type = type.getSuperclass()) {
                    try {
                        Method check = type.getDeclaredMethod("aA");
                        check.setAccessible(true);
                        return (Boolean) check.invoke(ime);
                    } catch (NoSuchMethodException ignored) { }
                }
                return null;
            } catch (Throwable failure) { report("route-confirmation", failure); return null; }
        }

        @Override public void returnToClipboard() {
            ClipboardNative1803.dispatch(keyboard, returnAction);
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
        return inputService.get();
    }

    private static boolean main() { return Looper.myLooper() == Looper.getMainLooper(); }

    private static void reportState(String state) {
        android.util.Log.w("GboardPatches", "Clipboard native search r12: " + state);
    }

    private static void report(String operation, Throwable failure) {
        android.util.Log.w("GboardPatches", "Clipboard native search: " + operation
                + " (" + failure.getClass().getSimpleName() + ")");
    }
}
