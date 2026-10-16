package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.ContextWrapper;
import android.inputmethodservice.InputMethodService;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputConnection;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 18.0.3 host for {@link ClipboardNativeSearchSession}.
 *
 * <p>Surface: the framework candidates area of the IME window
 * ({@link InputMethodService#setCandidatesView}), which sits above Gboard's own input view.
 * The alphabet keyboard therefore stays Gboard's native keyboard, in its own body.
 * Field: Gboard's {@code EditTextOnKeyboard} (implements {@code Loqa}), the same widget the
 * Jarvis prompt types into. Routing: {@code Loup.Z(Loqa;Z)V}, the service end of
 * {@code Lnvq.ak} (classes.dex 0x2eeddc → {@code Loup.aC(Loqa;ZZ)V}).
 *
 * <p>Fails closed: {@link #available} stays false until every binding below is reviewed
 * against the 18.0.3 binary. Until then the search button is not shown at all.
 */
public final class ClipboardNativeSearch1803 {
    static final String FIELD_CLASS = "com.google.android.libraries.inputmethod.widgets.EditTextOnKeyboard";
    static String providerInterface = "oqa"; // Loqa; replaceable only by tests.

    /** Each flag flips to true only with binary evidence (see clipboard-native-search-plan). */
    static final boolean ROUTING_RESTORE_REVIEWED = false;   // Loup.aC with the service as provider
    static final boolean ALPHABET_SWITCH_REVIEWED = false;  // clipboard body -> alphabet keyboard
    static final boolean RETURN_TO_CLIPBOARD_REVIEWED = false; // alphabet -> clipboard panel
    static final boolean INSETS_REVIEWED = false;           // candidates area touchable over Gboard insets

    private static ClipboardNativeSearchSession active;
    private static ClipboardSearchSurface surface;
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
            Host host = new Host(ime, (View) field);
            ClipboardNativeSearchSession session = new ClipboardNativeSearchSession(host);
            int height = Math.round(ime.getResources().getDisplayMetrics().heightPixels * 0.38f);
            surface = new ClipboardSearchSurface(ime, (View) field, height,
                    ClipboardSearchController.forImeMainThread(), new ClipboardSearchSurface.Listener() {
                        public void onPick(ClipboardSearchSurface.Clip clip) { session.pick(clip.text); finish(); }
                        public void onClose() { session.cancel(); finish(); }
                    });
            surface.setClips(clips);
            active = session;
            if (session.open()) return true;
            finish();
        } catch (Throwable failure) {
            report("open", failure);
            ClipboardNativeSearchSession session = active;
            if (session != null) session.end();
            finish();
        }
        return false;
    }

    /** Input session ended or the window hid: restore provider and surface, keep the keyboard. */
    public static void end() {
        ClipboardNativeSearchSession session = active;
        if (!main() || session == null) return;
        session.end();
        finish();
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
            return surface.isAttachedToWindow() || surface.getParent() != null;
        }

        @Override public void hideSurface() {
            ime.setCandidatesViewShown(false);
            // An empty placeholder: the candidates frame keeps no reference to the surface.
            ime.setCandidatesView(new View(ime));
        }

        @Override public boolean switchToAlphabet() {
            return false; // Binding pending (ALPHABET_SWITCH_REVIEWED): see plan, question A1.
        }

        @Override public boolean routeToField() {
            return setProvider(field);
        }

        @Override public void restoreRouting() {
            if (ROUTING_RESTORE_REVIEWED) setProvider(ime);
        }

        @Override public void returnToClipboard() {
            // Binding pending: see plan, question A2.
        }

        @Override public boolean commitToEditor(String text) {
            // The service connection is the editor's (journaled by the Undo wrapper when present).
            InputConnection connection = ime.getCurrentInputConnection();
            return connection != null && connection.commitText(text, 1);
        }

        /** Loup.Z(Loqa;Z)V → Loup.aC(provider, false, true): reset=false, as JarvisPromptKeyboard.K. */
        private boolean setProvider(Object provider) {
            try {
                Class<?> loqa = Class.forName(providerInterface, false, ime.getClass().getClassLoader());
                if (!loqa.isInstance(provider)) return false;
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
