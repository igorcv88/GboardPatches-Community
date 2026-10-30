package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.inputmethodservice.InputMethodService;
import android.content.Context;
import android.os.Looper;
import android.content.SharedPreferences;
import java.lang.ref.WeakReference;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardEditingAccessPointsRuntime {
    private static final EditorActionSession SESSION = new EditorActionSession();
    private static WeakReference<InputMethodService> service = new WeakReference<>(null);
    // Keep the adapter alive, but never retain the service or its InputConnection strongly.
    private static EditorActionSession.Editor editor;
    private static boolean journalReported;

    private GboardEditingAccessPointsRuntime() {}

    public static boolean enabled(Context context, EditorAction action) {
        try {
            SharedPreferences preferences = GboardPatchesSettings.preferences(context);
            return GboardEditingAccessPointsSettings.read(preferences,
                    GboardEditingAccessPointsSettings.ENABLED)
                    && GboardEditingAccessPointsSettings.read(preferences,
                    GboardEditingAccessPointsSettings.key(action));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Patched InputMethodService.getCurrentInputConnection() override. */
    public static android.view.inputmethod.InputConnection wrapInputConnection(
            android.view.inputmethod.InputConnection connection) {
        return JournalingInputConnection.wrap(connection);
    }

    /** Master switch for the current input service; false without an active service. */
    public static boolean featureEnabled() {
        try {
            InputMethodService current = service.get();
            return current != null && GboardEditingAccessPointsSettings.read(
                    GboardPatchesSettings.preferences(current), GboardEditingAccessPointsSettings.ENABLED);
        } catch (Throwable ignored) { return false; }
    }

    public static void start(Object receiver) {
        end();
        try {
            if (receiver instanceof InputMethodService ime) {
                service = new WeakReference<>(ime);
                dev.jason.gboardpatches.extension.clipboard.TermuxClipboardSuggestion.start(ime);
                WeakReference<InputMethodService> reference = service;
                editor = new EditorActionSession.Editor() {
                    @Override public android.view.inputmethod.InputConnection connection() {
                        InputMethodService current = reference.get();
                        // The patched service returns the journaling wrapper; actions use the editor's own connection.
                        return current == null ? null
                                : JournalingInputConnection.unwrap(current.getCurrentInputConnection());
                    }
                    @Override public boolean allowSnapshots() {
                        InputMethodService current = reference.get();
                        android.view.inputmethod.EditorInfo info = current == null
                                ? null : current.getCurrentInputEditorInfo();
                        if (info == null || (info.imeOptions &
                                android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return false;
                        int type = info.inputType;
                        int variation = type & android.text.InputType.TYPE_MASK_VARIATION;
                        return (type & android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_TEXT
                                && variation != android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                                && variation != android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                                && variation != android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
                    }
                };
                SESSION.begin(editor);
                boolean record = enabled(ime, EditorAction.UNDO) && editor.allowSnapshots();
                JournalingInputConnection.setPolicy(() -> record);
                if (record && !journalReported) {
                    android.view.inputmethod.InputConnection current = ime.getCurrentInputConnection();
                    if (current != null && !(current instanceof JournalingInputConnection)) {
                        journalReported = true;
                        android.util.Log.w("GboardPatches", "Undo journal inactive: service connection is not wrapped");
                    }
                }
            }
        } catch (Throwable ignored) {
            end();
        }
    }

    public static void end() {
        dev.jason.gboardpatches.extension.clipboard.TermuxClipboardSuggestion.suspend();
        try {
            String counters = JournalingInputConnection.drainCounters();
            if (counters != null) android.util.Log.i("GboardPatches", "Undo journal session: " + counters);
        } catch (Throwable ignored) { }
        JournalingInputConnection.setPolicy(null);
        JournalingInputConnection.resetAll();
        dev.jason.gboardpatches.extension.quicktoolbar.QuickToolbar1803Runtime.reset();
        SESSION.end();
        service.clear();
        editor = null;
    }

    public static void onSelectionUpdated(int start, int end, int candidateStart, int candidateEnd) {
        try {
            SESSION.onSelectionUpdated(start, end, candidateStart, candidateEnd);
        } catch (Throwable ignored) { SESSION.end(); }
    }

    public static void beforeNativeClipboardCard() {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) SESSION.nativeClipboardAttempt();
        } catch (Throwable ignored) { }
    }

    public static void beforeNativeClipboardText(String text) {
        try {
            if (Looper.myLooper() != Looper.getMainLooper()
                    || !enabled(service.get(), EditorAction.UNDO)) return;
            long token = SESSION.beginNativePaste(text);
            if (token != 0) new android.os.Handler(Looper.getMainLooper())
                    .postDelayed(() -> SESSION.expireNativePaste(token), 1500);
        } catch (Throwable ignored) { /* Capture is optional; leave stock insertion untouched. */ }
    }

    public static void afterNativeClipboardText() {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) SESSION.observeNativePaste();
        } catch (Throwable ignored) { /* The selection observer may confirm a queued dispatch. */ }
    }

    /** Null means no active shared session; callers may then use their existing native path. */
    public static Boolean executeForConnection(android.view.inputmethod.InputConnection connection, int actionId) {
        try {
            InputMethodService current = service.get();
            if (current == null || Looper.myLooper() != Looper.getMainLooper()
                    || JournalingInputConnection.unwrap(current.getCurrentInputConnection())
                            != JournalingInputConnection.unwrap(connection)) return null;
            for (EditorAction action : EditorAction.values()) {
                if (action.contextMenuId == actionId) return SESSION.execute(action);
            }
        } catch (Throwable ignored) { return false; }
        return null;
    }

    public static void click(EditorAction action) {
        try {
            // Stock AP clicks are synchronous UI callbacks. Do not queue stale edits.
            if (Looper.myLooper() == Looper.getMainLooper() && enabled(service.get(), action)) {
                boolean applied = SESSION.execute(action);
                if (!applied && (action == EditorAction.UNDO || action == EditorAction.REDO)) {
                    // The stock Gboard button can undo a paste even when the editor rejects
                    // android.R.id.undo. Forward the conventional keyboard shortcut as the
                    // host-owned fallback; never replay snapshots of unverified text.
                    InputMethodService current = service.get();
                    android.view.inputmethod.InputConnection connection = current == null
                            ? null : JournalingInputConnection.unwrap(current.getCurrentInputConnection());
                    applied = HostHistoryShortcuts.send(connection, action);
                    android.util.Log.i("GboardHistory", "action=" + action
                            + " route=host-shortcut accepted=" + applied);
                }
                if (applied && (action == EditorAction.UNDO || action == EditorAction.REDO)) {
                    dev.jason.gboardpatches.extension.quicktoolbar.QuickToolbar1803Runtime
                            .afterSuccessfulHistoryAction();
                }
            }
        } catch (Throwable ignored) {
            // Descriptor callbacks must not escape into Gboard.
        }
    }
}
