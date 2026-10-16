package dev.jason.gboardpatches.extension.quicktoolbar;

import android.content.res.TypedArray;
import android.content.SharedPreferences;
import java.util.concurrent.ConcurrentHashMap;
import android.os.Looper;
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
    private QuickToolbar1803Runtime() {}

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
            clear();
            if (!(nativeBar instanceof View nativeView)) return;
            Object nativeManager = call(value, "G");
            if (nativeManager == null) return;
            controller = new WeakReference<>(value);
            manager = new WeakReference<>(nativeManager);
            bar = new WeakReference<>(nativeView);
            Object candidates = candidateOwner.get();
            if (candidates != null) onCandidates(candidates);
        } catch (Throwable ignored) { clear(); }
    }

    public static void onCandidates(Object value) {
        if (!main() || applying) return;
        try {
            Object nativeCandidate = field(value, "c");
            if (!(nativeCandidate instanceof View candidate)) return;
            View rootView = candidate.getRootView();
            View nativeBar = bar.get();
            if (nativeBar == null || candidate.getRootView() != nativeBar.getRootView()) return;
            View left = rootView.findViewById(0x7f0b053e);
            if (left == null || !isToggleCode(primaryCode(left))) { clearBinding(); return; }
            if (button.get() != left || binding == null) {
                clearBinding();
                Object contents = call(left, "d");
                if (!(contents instanceof ViewGroup content) || contents == left) return;
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
        } catch (Throwable ignored) { clearBinding(); }
    }

    public static void onCandidatesCleared(Object owner) {
        if (!main() || applying || owner != candidateOwner.get()) return;
        // Native favorites suppress the candidate view without ending the text session.
        // Actual empty candidate lists arrive independently through b(List, ...).
        if (binding != null && binding.mode() == QuickToolbarState.Mode.QUICK_TOOLS && candidateListNonempty) return;
        clearBinding();
    }

    public static boolean handleEvent(Object nativeManager, Object event) {
        if (!main() || applying || nativeManager != manager.get() || binding == null) return false;
        try {
            Object action = call(event, "g");
            if (action == null || !isToggleCode(((Number) field(action, "c")).intValue())) return false;
            View left = button.get(), root = header.get();
            if (left == null || root == null || !root.isShown() || !isToggleCode(primaryCode(left))) {
                clearBinding(); return false;
            }
            return binding.onMenuEvent();
        } catch (Throwable ignored) {
            boolean wasArrow = binding != null && binding.mode() != QuickToolbarState.Mode.STOCK_TOOLBAR;
            clearBinding();
            return wasArrow;
        }
    }

    private static boolean showFavorites() {
        Object target = manager.get();
        if (target == null) return false;
        applying = true;
        try {
            Class<?> reason = target.getClass().getClassLoader().loadClass("pfz");
            Field manual = reason.getDeclaredField("b"); manual.setAccessible(true);
            Method show = target.getClass().getDeclaredMethod("M", boolean.class, boolean.class, reason, boolean.class);
            show.setAccessible(true); show.invoke(target, true, false, manual.get(null), false);
            return Boolean.TRUE.equals(field(controller.get(), "o"))
                    && !Boolean.TRUE.equals(field(controller.get(), "p")) && bar.get() != null && bar.get().isShown();
        } catch (Throwable ignored) { return false; }
        finally { applying = false; }
    }

    private static void hideFavorites() {
        Object target = manager.get();
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
    public static void reset() { if (main()) clearBinding(); }
    public static void clear() {
        clearBinding(); controller.clear(); manager.clear(); bar.clear();
        candidateOwner.clear(); candidateListNonempty = false;
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(SETTINGS_CHANGED);
        preferences = null;
    }
}
