package dev.jason.gboardpatches.extension.quicktoolbar;

import android.content.res.TypedArray;
import android.content.SharedPreferences;
import java.util.concurrent.ConcurrentHashMap;
import android.os.Looper;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import dev.jason.gboardpatches.extension.editingaccesspoints.GboardEditingAccessPointsSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

/** Only original 18.0.3 members reviewed against APK SHA-256 30992621…05e5b3. */
public final class QuickToolbar1803Runtime {
    private static WeakReference<Object> controller = new WeakReference<>(null);
    private static WeakReference<Object> manager = new WeakReference<>(null);
    private static WeakReference<View> bar = new WeakReference<>(null);
    private static WeakReference<View> header = new WeakReference<>(null);
    private static WeakReference<View> button = new WeakReference<>(null);
    private static WeakReference<Object> candidateOwner = new WeakReference<>(null);
    private static boolean candidateListNonempty;
    private static QuickToolbarBinding binding;
    private static final View.OnAttachStateChangeListener DETACH = new View.OnAttachStateChangeListener() {
        public void onViewAttachedToWindow(View view) { }
        public void onViewDetachedFromWindow(View view) {
            if (button.get() == view) clearBinding();
        }
    };
    private static boolean applying;
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Field>> FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Method>> METHODS = new ConcurrentHashMap<>();
    private static SharedPreferences preferences;
    private static boolean enabled = true;
    private static final SharedPreferences.OnSharedPreferenceChangeListener SETTINGS_CHANGED = (prefs, key) -> {
        if (key == null || GboardEditingAccessPointsSettings.ENABLED.equals(key))
            enabled = GboardEditingAccessPointsSettings.read(prefs, GboardEditingAccessPointsSettings.ENABLED);
    };
    /** Favorites shown by the event fallback, without the arrow binding on the button. */
    private static boolean fallbackShown;
    /** The native manager may remain valid when an AP controller binding is unavailable. */
    private static WeakReference<Object> fallbackManager = new WeakReference<>(null);
    /** Prevent callbacks posted before a toggle/session reset from restoring a closed row. */
    private static long fallbackRevision;
    private static final java.util.Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private QuickToolbar1803Runtime() {}

    /** Each distinct reason for leaving the stock path is logged once per process; no user data. */
    static void diagnose(String reason) {
        if (REPORTED.add(reason)) {
            try { android.util.Log.w("GboardPatches", "Quick toolbar: " + reason); }
            catch (Throwable ignored) { }
        }
    }

    public static void onCandidateList(Object owner, Object candidates) {
        if (!main() || applying) return;
        candidateOwner = new WeakReference<>(owner);
        candidateListNonempty = candidates instanceof java.util.List<?> list && !list.isEmpty();
    }

    public static void onControllerView(Object value) {
        if (!main() || applying) return;
        try {
            Object nativeBar = field(value, "j");
            if (controller.get() == value && bar.get() == nativeBar) return;
            // The 18.0.3 controller does not always expose a View at field j.
            // A failed binding must not erase the active fallback or its candidate state.
            if (!(nativeBar instanceof View nativeView)) {
                diagnose("controller bar is not a view");
                clearBinding();
                controller.clear(); manager.clear(); bar.clear();
                return;
            }
            // Leso can deliver candidates before the new AP controller is attached.
            Object pendingOwner = candidateOwner.get();
            boolean pendingNonempty = candidateListNonempty;
            Object pendingFallbackManager = fallbackManager.get();
            boolean keepFallback = fallbackShown && pendingFallbackManager != null;
            long stickyRevision = fallbackRevision;
            clear();
            if (keepFallback) {
                fallbackShown = true;
                fallbackManager = new WeakReference<>(pendingFallbackManager);
                // A view rebind within the same input session must not cancel a pending
                // post-Undo/Redo restoration scheduled before Gboard redrew candidates.
                fallbackRevision = stickyRevision;
            }
            if (pendingOwner != null) {
                candidateOwner = new WeakReference<>(pendingOwner);
                candidateListNonempty = pendingNonempty;
            }
            Object nativeManager = call(value, "G");
            if (nativeManager == null) { diagnose("controller has no manager"); return; }
            controller = new WeakReference<>(value);
            manager = new WeakReference<>(nativeManager);
            bar = new WeakReference<>(nativeView);
            Object candidates = candidateOwner.get();
            if (candidates != null) onCandidates(candidates);
        } catch (Throwable failure) { diagnose("controller binding failed: " + failure.getClass().getSimpleName()); clear(); }
    }

    public static void onCandidates(Object value) {
        if (!main() || applying) return;
        try {
            Object nativeCandidate = field(value, "c");
            if (!(nativeCandidate instanceof View candidate)) { diagnose("candidate view missing"); return; }
            View rootView = candidate.getRootView();
            View nativeBar = bar.get();
            if (nativeBar == null || candidate.getRootView() != nativeBar.getRootView()) {
                diagnose(nativeBar == null ? "candidates before favorites bar" : "candidates in another window");
                return;
            }
            View left = rootView.findViewById(0x7f0b053e);
            if (left == null) { diagnose("left header key 0x7f0b053e missing"); clearBinding(); return; }
            if (!isToggleCode(primaryCode(left))) { diagnose("left header key has another action"); clearBinding(); return; }
            if (button.get() != left || binding == null) {
                clearBinding();
                Object contents = call(left, "d");
                if (!(contents instanceof ViewGroup content) || contents == left) {
                    diagnose("left header key has no content group"); return;
                }
                TypedArray colors = left.getContext().obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
                int color;
                try { color = colors.getColor(0, 0xff808080); } finally { colors.recycle(); }
                binding = new QuickToolbarBinding(left, content, color, new QuickToolbarBinding.Favorites() {
                    public boolean show() { return showFavorites(); }
                    public void hide() { hideFavorites(); }
                });
                button = new WeakReference<>(left);
                left.addOnAttachStateChangeListener(DETACH);
            }
            header = new WeakReference<>(rootView);
            boolean active = rootView.isShown() && rootView.isAttachedToWindow()
                    && left.isShown() && !((Boolean) field(controller.get(), "p"));
            boolean hasCandidates = candidateOwner.get() == value && candidateListNonempty
                    && (((Number) field(value, "g")).intValue() > 0
                            || binding.mode() == QuickToolbarState.Mode.QUICK_TOOLS);
            if (preferences == null) {
                preferences = GboardPatchesSettings.preferences(left.getContext());
                enabled = GboardEditingAccessPointsSettings.read(preferences, GboardEditingAccessPointsSettings.ENABLED);
                preferences.registerOnSharedPreferenceChangeListener(SETTINGS_CHANGED);
            }
            binding.update(enabled,
                    active && (candidate.isShown() || binding.mode() == QuickToolbarState.Mode.QUICK_TOOLS),
                    hasCandidates);
        } catch (Throwable failure) {
            diagnose("candidate binding failed: " + failure.getClass().getSimpleName());
            clearBinding();
        }
    }

    public static void onCandidatesCleared(Object owner) {
        if (!main() || applying || owner != candidateOwner.get()) return;
        // Native favorites suppress the candidate view without ending the text session.
        // Actual empty candidate lists arrive independently through b(List, ...).
        if (binding != null && binding.mode() == QuickToolbarState.Mode.QUICK_TOOLS && candidateListNonempty) return;
        clearBinding();
    }

    public static boolean handleEvent(Object nativeManager, Object event) {
        if (!main() || applying || nativeManager == null) return false;
        int code;
        try {
            Object action = call(event, "g");
            if (action == null) return false;
            code = ((Number) field(action, "c")).intValue();
        } catch (Throwable failure) {
            diagnose("event action unreadable: " + failure.getClass().getSimpleName());
            return false;
        }
        if (code != -40005 && code != -40006) return false;
        if (binding != null && nativeManager == manager.get()) {
            try {
                if (!isToggleCode(code)) return false;
                View left = button.get(), root = header.get();
                if (left == null || root == null || !root.isShown() || !isToggleCode(primaryCode(left))) {
                    diagnose("bound button no longer matches the header");
                    clearBinding();
                } else {
                    return binding.onMenuEvent();
                }
            } catch (Throwable failure) {
                boolean wasArrow = binding != null && binding.mode() != QuickToolbarState.Mode.STOCK_TOOLBAR;
                diagnose("bound event failed: " + failure.getClass().getSimpleName());
                clearBinding();
                if (wasArrow) return true;
            }
        } else if (binding != null) {
            diagnose("event manager differs from bound controller");
        }
        return fallbackEvent(nativeManager, code);
    }

    /**
     * Used when the arrow binding is absent: while suggestions are active the shortcuts key
     * toggles the native favorites row instead of opening the full panel over the keys.
     */
    static boolean fallbackEvent(Object nativeManager, int code) {
        if (!featureEnabled()) return false;
        // Only favorites this fallback opened are closed here: in the normal keyboard state the
        // native row is visible by itself and the key must keep opening the full panel.
        if (fallbackShown) {
            fallbackShown = false;
            fallbackManager.clear();
            fallbackRevision++;
            if (favoritesVisible()) {
                hideFavorites(nativeManager);
                return true;
            }
        }
        if (code != -40005) return false;
        if (!candidateListNonempty || candidateOwner.get() == null) {
            diagnose("stock panel kept: no active suggestions");
            return false;
        }
        if (!invokeShowFavorites(nativeManager)) {
            diagnose("favorites fallback failed; stock panel kept");
            return false;
        }
        fallbackShown = true;
        fallbackManager = new WeakReference<>(nativeManager);
        fallbackRevision++;
        diagnose("favorites shown by event fallback (no button binding)");
        return true;
    }

    /**
     * A successful Undo/Redo can make Gboard redraw the native candidate row over the
     * favorites. Reassert the *user-selected* favorites mode after the redraw, without
     * changing input history or opening the full shortcuts panel.
     */
    public static void afterSuccessfulHistoryAction() {
        if (!main() || !fallbackShown) return;
        Object target = fallbackManager.get();
        if (target == null) return;
        long expectedRevision = fallbackRevision;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!fallbackShown || fallbackRevision != expectedRevision
                    || target != fallbackManager.get() || !featureEnabled() || applying) return;
            if (invokeShowFavorites(target)) diagnose("favorites restored after Undo/Redo");
        }, 48L);
    }

    private static boolean favoritesVisible() {
        Object nativeController = controller.get();
        if (nativeController != null) {
            try { return Boolean.TRUE.equals(field(nativeController, "o")); }
            catch (Throwable ignored) { /* Unknown: assume the row this fallback opened is still shown. */ }
        }
        return true;
    }

    static Boolean enabledOverride; // Tests only.

    private static boolean featureEnabled() {
        if (enabledOverride != null) return enabledOverride;
        if (preferences != null) return enabled;
        try {
            return dev.jason.gboardpatches.extension.editingaccesspoints.GboardEditingAccessPointsRuntime.featureEnabled();
        } catch (Throwable ignored) { return false; }
    }

    private static boolean showFavorites() {
        Object target = manager.get();
        if (target == null || !invokeShowFavorites(target)) return false;
        try {
            return Boolean.TRUE.equals(field(controller.get(), "o"))
                    && !Boolean.TRUE.equals(field(controller.get(), "p")) && bar.get() != null && bar.get().isShown();
        } catch (Throwable ignored) { return false; }
    }

    /** Reviewed Lmln.M(ZZLpfz;Z)V: native ordered favorites, never Lmlh.D (all shortcuts). */
    private static boolean invokeShowFavorites(Object target) {
        applying = true;
        try {
            Class<?> reason = target.getClass().getClassLoader().loadClass("pfz");
            Field manual = reason.getDeclaredField("b"); manual.setAccessible(true);
            Method show = target.getClass().getDeclaredMethod("M", boolean.class, boolean.class, reason, boolean.class);
            show.setAccessible(true); show.invoke(target, true, false, manual.get(null), false);
            return true;
        } catch (Throwable failure) {
            diagnose("show favorites failed: " + failure.getClass().getSimpleName());
            return false;
        } finally { applying = false; }
    }

    private static void hideFavorites() { hideFavorites(manager.get()); }

    private static void hideFavorites(Object target) {
        if (target == null || applying) return;
        applying = true;
        try {
            Method hide = target.getClass().getDeclaredMethod("y", boolean.class, boolean.class);
            hide.setAccessible(true); hide.invoke(target, false, false);
        } catch (Throwable ignored) { /* Native lifecycle also removes its own bar. */ }
        finally { applying = false; }
    }

    private static int primaryCode(View view) throws Exception {
        Object action = call(view, "h");
        return action == null ? 0 : ((Number) field(action, "c")).intValue();
    }
    private static boolean isToggleCode(int code) {
        return code == -40005 || code == -40006 && binding != null
                && binding.mode() == QuickToolbarState.Mode.QUICK_TOOLS;
    }
    private static boolean main() { return Looper.myLooper() == Looper.getMainLooper(); }
    private static Object field(Object receiver, String name) throws Exception {
        if (receiver == null) return null;
        ConcurrentHashMap<String, Field> fields = FIELDS.computeIfAbsent(receiver.getClass(), type -> new ConcurrentHashMap<>());
        Field field = fields.get(name);
        if (field == null) {
            for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
                try { field = type.getDeclaredField(name); field.setAccessible(true); break; }
                catch (NoSuchFieldException ignored) { }
            }
            if (field == null) throw new NoSuchFieldException(name);
            fields.put(name, field);
        }
        return field.get(receiver);
    }
    private static Object call(Object receiver, String name) throws Exception {
        ConcurrentHashMap<String, Method> methods = METHODS.computeIfAbsent(receiver.getClass(), type -> new ConcurrentHashMap<>());
        Method method = methods.get(name);
        if (method == null) {
            for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
                try { method = type.getDeclaredMethod(name); method.setAccessible(true); break; }
                catch (NoSuchMethodException ignored) { }
            }
            if (method == null) throw new NoSuchMethodException(name);
            methods.put(name, method);
        }
        return method.invoke(receiver);
    }
    private static void clearBinding() {
        QuickToolbarBinding previous = binding;
        binding = null;
        View previousButton = button.get();
        if (previousButton != null) previousButton.removeOnAttachStateChangeListener(DETACH);
        button.clear(); header.clear();
        if (previous != null) previous.close();
    }
    public static void reset() {
        if (!main()) return;
        // A new input session must not inherit the previous editor's suggestion state.
        clearBinding(); fallbackShown = false; fallbackManager.clear(); fallbackRevision++;
        candidateOwner.clear(); candidateListNonempty = false;
    }
    public static void clear() {
        clearBinding(); controller.clear(); manager.clear(); bar.clear(); fallbackShown = false;
        fallbackManager.clear(); fallbackRevision++;
        candidateOwner.clear(); candidateListNonempty = false;
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(SETTINGS_CHANGED);
        preferences = null;
    }
}
