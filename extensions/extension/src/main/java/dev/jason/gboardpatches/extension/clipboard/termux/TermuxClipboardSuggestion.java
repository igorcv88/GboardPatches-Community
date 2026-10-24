package dev.jason.gboardpatches.extension.clipboard;

import android.app.AlertDialog;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import java.util.Map;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import dev.jason.gboardpatches.extension.manualincognito.GboardManualIncognitoRuntime;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Exact 18.0.3 adapter. Uses native source, chip layout and proactive-suggestion holder. */
public final class TermuxClipboardSuggestion {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final TermuxClipboardTicket TICKET = new TermuxClipboardTicket();
    private static WeakReference<Object> helper = new WeakReference<>(null);
    private static WeakReference<InputMethodService> service = new WeakReference<>(null);
    private static View chip;
    private static Object displayedSource;
    private static Object category;
    private static AlertDialog confirmation;
    private static Runnable expiry;
    private static Object suggestion, primarySurface;
    private static int publishAttempts;
    private static WeakReference<View> header = new WeakReference<>(null);
    private static boolean headerListening, refreshPosted;
    private static String lastStatus;
    private static boolean admittedSession;
    private static final View.OnLayoutChangeListener CHIP_LAYOUT = (view, l, t, r, b, ol, ot, or, ob) -> {
        if (view == chip && actuallyVisible()) { status("visible"); stopHeaderListener(); }
    };
    private static final ViewTreeObserver.OnGlobalLayoutListener HEADER_LAYOUT = () -> {
        if (chip == null) { stopHeaderListener(); return; }
        if (actuallyVisible()) { status("visible"); stopHeaderListener(); }
        else if (publishAttempts < 3) scheduleRefresh();
        else status("pending-holder"); // Observe later visibility, but never dispatch beyond the cap.
    };

    /** Fixed state names only. Never logs text, clip labels, timestamp or exception messages. */
    private static void status(String state) {
        if (!admittedSession || state.equals(lastStatus)) return;
        lastStatus = state;
        android.util.Log.i("GboardTermuxClip", "r3 " + state);
    }

    private static boolean actuallyVisible() {
        if (chip == null || !chip.isAttachedToWindow() || !chip.isShown()
                || chip.getWindowVisibility() != View.VISIBLE
                || chip.getWidth() <= 0 || chip.getHeight() <= 0) return false;
        for (View view = chip; view != null; ) {
            if (view.getAlpha() <= 0f) return false;
            view = view.getParent() instanceof View parent ? parent : null;
        }
        android.graphics.Rect bounds = new android.graphics.Rect();
        return chip.getGlobalVisibleRect(bounds) && !bounds.isEmpty();
    }

    /** Input view initialization, independent of prediction lists and clipboard notifications. */
    public static void headerPrepared(Object view, Object surface) {
        if (!(view instanceof View root) || !(surface instanceof Enum<?> kind)
                || !"HEADER".equals(kind.name())) return;
        stopHeaderListener();
        header = new WeakReference<>(root);
        if (service.get() != null) {
            watchHeader(); scheduleRefresh();
        }
    }

    private static void watchHeader() {
        View root = header.get();
        if (root != null && !headerListening) {
            root.getViewTreeObserver().addOnGlobalLayoutListener(HEADER_LAYOUT);
            headerListening = true;
        }
    }
    private static void stopHeaderListener() {
        View root = header.get();
        if (root != null && headerListening && root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(HEADER_LAYOUT);
        }
        headerListening = false;
    }
    private static void scheduleRefresh() {
        if (refreshPosted) return;
        refreshPosted = true;
        InputMethodService expected = service.get();
        UI.post(() -> {
            refreshPosted = false;
            if (expected != null && service.get() == expected) refresh();
        });
    }

    private TermuxClipboardSuggestion() { }

    /** True consumes ONLY the Termux TYPE_NULL helper path, including private/empty sessions. */
    public static boolean handle(Object value) {
        try {
            EditorInfo info = (EditorInfo) field(value, "m");
            if (info == null || !"com.termux".equals(info.packageName) || info.inputType != 0) return false;
        } catch (Throwable ignored) { return false; }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            WeakReference<Object> pending = new WeakReference<>(value);
            UI.post(() -> { Object current = pending.get(); if (current != null) handle(current); });
            return true;
        }
        try {
            admittedSession = true;
            if (helper.get() != value) { clear(); helper = new WeakReference<>(value); }
            refresh();
        } catch (Throwable ignored) { clear(); }
        // Fail closed only AFTER exact package/input-shape admission.
        return true;
    }

    public static void start(Object receiver) {
        if (!(receiver instanceof InputMethodService ime)) return;
        service = new WeakReference<>(ime);
        EditorInfo info = ime.getCurrentInputEditorInfo();
        if (info != null && "com.termux".equals(info.packageName)) {
            admittedSession = true; lastStatus = null; status("session-bound"); watchHeader();
        }
        // Native t/x can precede onStartInputView's exit. Coalesce after the view is ready.
        Object expected = helper.get();
        UI.post(() -> { if (service.get() == ime && helper.get() == expected) refresh(); });
    }

    public static void suspend() { clear(); service.clear(); admittedSession = false; }

    public static void closed(Object value) {
        if (helper.get() != value) return;
        clear(); helper.clear();
    }

    private static Source source() throws Exception {
        Object owner = helper.get();
        InputMethodService ime = service.get();
        if (owner == null || ime == null) { status("waiting-session-helper"); return null; }
        EditorInfo info = (EditorInfo) field(owner, "m");
        EditorInfo current = ime.getCurrentInputEditorInfo();
        // Gboard may pass an EditorInfo copy to modules. Compare editor identity fields,
        // not object identity; the active connection and lifecycle ticket guard insertion.
        if (info == null || current == null || !"com.termux".equals(info.packageName)
                || !"com.termux".equals(current.packageName) || info.inputType != 0
                || current.inputType != 0 || info.fieldId != current.fieldId) { status("editor-mismatch"); return null; }
        // Lfif.o selects the InputContextNotification branch. Its producer is the
        // EditorInfo identity comparison in Lnvk.I, NOT an incognito predicate.
        // Stock Leqy.G uses IME_FLAG_NO_PERSONALIZED_LEARNING; the extension adds
        // its latched manual-incognito state. Check both EditorInfo copies before text.
        if (GboardManualIncognitoRuntime.applyIncognitoPredicate(false)) {
            status("private-manual-incognito"); return null;
        }
        if (((info.imeOptions | current.imeOptions) & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) {
            status("private-editor"); return null;
        }
        ClassLoader loader = owner.getClass().getClassLoader();
        boolean disabled = (Boolean) invokeStatic(loader, "npc", "u",
                new Class<?>[]{String.class, String.class, EditorInfo.class}, null, "disableAutoPaste", info);
        boolean unlocked = (Boolean) invokeStatic(loader, "qqw", "e", new Class<?>[0]);
        if (disabled || !unlocked) { status(disabled ? "disabled-by-editor" : "device-not-unlocked"); return null; }
        Object clips = field(owner, "k");
        if (!(clips instanceof List<?> list) || list.isEmpty()) { status("source-empty"); return null; }
        Object clip = list.get(0);
        if (!Boolean.FALSE.equals(call(clip, "m", new Class<?>[0]))) { status("sensitive-source"); return null; }
        Object instant = field(clip, "e");
        long copiedAt = (Long) call(instant, "toEpochMilli", new Class<?>[0]);
        String text = (String) call(clip, "i", new Class<?>[0]);
        if (!TermuxClipboardPolicy.eligible(info.packageName, info.inputType, info.imeOptions,
                false, false, disabled, unlocked, text, copiedAt, System.currentTimeMillis())) { status("source-policy-blocked"); return null; }
        InputConnection connection = ime.getCurrentInputConnection();
        return connection == null ? null : new Source(owner, ime, clip, text, copiedAt, connection);
    }

    private static void refresh() {
        try {
            Source source = source();
            if (source == null) { clear(); return; }
            long ticket = TICKET.offer(source.clip);
            if (!TICKET.active(ticket)) return;
            if (displayedSource == source.clip && chip != null) {
                if (actuallyVisible()) { status("visible"); stopHeaderListener(); return; }
                publish(source); return;
            }
            clearSurface();
            ClassLoader loader = source.owner.getClass().getClassLoader();
            Class<?> surface = loader.loadClass("ppe");
            primarySurface = staticField(surface, "a");
            Class<?> nativeClip = loader.loadClass("fih");
            // Reviewed private native text-chip builder; replace its listener BEFORE publication.
            android.content.Context themedContext = source.ime;
            try {
                Object module = field(source.owner, "l");
                if (module != null && call(module, "a", new Class<?>[0]) instanceof android.content.Context context) {
                    themedContext = context;
                }
            } catch (ReflectiveOperationException ignored) { /* Service context remains a valid fallback. */ }
            View view = (View) call(source.owner, "y", new Class<?>[]{android.content.Context.class,
                    nativeClip, boolean.class, int.class, boolean.class, surface},
                    themedContext, source.clip, false, 2, true, primarySurface);
            if (view == null) { clear(); return; }
            view.setVisibility(View.VISIBLE);
            view.addOnLayoutChangeListener(CHIP_LAYOUT);
            view.setOnLongClickListener(null);
            view.setOnClickListener(v -> clicked(ticket));
            java.lang.reflect.Constructor<?> constructor = loader.loadClass("qju").getDeclaredConstructor();
            constructor.setAccessible(true);
            Object builder = constructor.newInstance();
            Class<?> categoryType = loader.loadClass("qjw");
            Object clipboardCategory = staticField(categoryType, "c");
            call(builder, "b", new Class<?>[]{categoryType}, clipboardCategory);
            call(builder, "d", new Class<?>[]{String.class}, "clipboard");
            call(builder, "e", new Class<?>[]{List.class}, List.of(view));
            suggestion = call(builder, "a", new Class<?>[0]);
            chip = view; displayedSource = source.clip; category = clipboardCategory;
            status("chip-built");
            publish(source);
            long delay = Math.max(1, source.copiedAt + TermuxClipboardPolicy.MAX_AGE_MS - System.currentTimeMillis() + 1);
            expiry = () -> { if (TICKET.active(ticket)) clear(); };
            UI.postDelayed(expiry, delay);
        } catch (Throwable failure) {
            status("binding-failed:" + failure.getClass().getSimpleName()); clear();
        }
    }

    private static void publish(Source source) throws Exception {
        if (publishAttempts >= 3) { status("pending-holder"); return; }
        publishAttempts++;
        watchHeader();
        ClassLoader loader = source.owner.getClass().getClassLoader();
        Class<?> priority = loader.loadClass("pfz");
        // The stock clipboard uses the per-surface overload. The single-suggestion overload
        // performs holder selection and may defer to a different/unavailable surface.
        invokeStatic(loader, "qjr", "c", new Class<?>[]{Map.class, priority},
                Map.of(primarySurface, suggestion), staticField(priority, "c"));
        status(actuallyVisible() ? "visible" : "requested-header");
        if (actuallyVisible()) stopHeaderListener();
    }

    private static void clicked(long ticket) {
        try {
            Source source = source();
            if (source == null) { clear(); return; }
            if (!TICKET.active(ticket) || source.clip != displayedSource || confirmation != null) return;
            if (!TermuxClipboardPolicy.needsConfirmation(source.text)) { commit(ticket, source); return; }
            // Termux commitText can execute embedded newlines. Require a separate explicit consent.
            boolean pt = source.ime.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("pt");
            AlertDialog dialog = new AlertDialog.Builder(source.ime)
                    .setTitle(pt ? "Colar no terminal?" : "Paste into terminal?")
                    .setMessage(pt ? "O texto contém quebras de linha ou tabulações e pode executar comandos. Colar o conteúdo completo?"
                            : "The text contains line breaks or tabs and may execute commands. Paste the full content?")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(pt ? "Colar" : "Paste", (d, which) -> commit(ticket, source))
                    .create();
            Window window = dialog.getWindow();
            if (window == null || chip == null || chip.getWindowToken() == null) return;
            WindowManager.LayoutParams params = window.getAttributes();
            params.token = chip.getWindowToken();
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            confirmation = dialog;
            dialog.setOnDismissListener(d -> { if (confirmation == dialog) confirmation = null; });
            dialog.show();
        } catch (Throwable ignored) { clear(); }
    }

    private static void commit(long ticket, Source expected) {
        try {
            Source current = source();
            if (current == null || current.owner != expected.owner || current.clip != expected.clip
                    || current.ime != expected.ime || current.connection != expected.connection
                    || !current.text.equals(expected.text) || !TICKET.claim(ticket)) return;
            clearSurface();
            // One invocation, no retry on false/exception, no key events and no synthetic Enter.
            current.connection.commitText(current.text, 1);
        } catch (Throwable ignored) { clearSurface(); }
    }

    private static void clear() { TICKET.invalidate(); clearSurface(); }

    private static void clearSurface() {
        stopHeaderListener();
        suggestion = null; primarySurface = null; publishAttempts = 0;
        if (expiry != null) UI.removeCallbacks(expiry);
        expiry = null;
        if (confirmation != null) { confirmation.dismiss(); confirmation = null; }
        if (chip != null) { chip.removeOnLayoutChangeListener(CHIP_LAYOUT); chip.setOnClickListener(null); chip = null; }
        displayedSource = null;
        Object oldCategory = category; category = null;
        if (oldCategory != null) {
            try { invokeStatic(oldCategory.getClass().getClassLoader(), "qjq", "c",
                    new Class<?>[]{oldCategory.getClass(), boolean.class}, oldCategory, true); }
            catch (Throwable ignored) { }
        }
    }

    private static final class Source {
        final Object owner, clip;
        final InputMethodService ime;
        final String text;
        final long copiedAt;
        final InputConnection connection;
        Source(Object owner, InputMethodService ime, Object clip, String text,
                long copiedAt, InputConnection connection) {
            this.owner = owner; this.ime = ime; this.clip = clip; this.text = text;
            this.copiedAt = copiedAt; this.connection = connection;
        }
    }

    private static Object field(Object receiver, String name) throws Exception {
        return staticOrInstanceField(receiver.getClass(), receiver, name);
    }
    private static Object staticField(Class<?> type, String name) throws Exception {
        return staticOrInstanceField(type, null, name);
    }
    private static Object staticOrInstanceField(Class<?> type, Object receiver, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(receiver);
    }
    private static Object call(Object receiver, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = receiver.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true); return method.invoke(receiver, arguments);
    }
    private static Object invokeStatic(ClassLoader loader, String owner, String name,
            Class<?>[] types, Object... arguments) throws Exception {
        Method method = loader.loadClass(owner).getDeclaredMethod(name, types);
        method.setAccessible(true); return method.invoke(null, arguments);
    }
}
