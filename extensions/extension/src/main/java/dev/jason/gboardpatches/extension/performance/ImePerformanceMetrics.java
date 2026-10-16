package dev.jason.gboardpatches.extension.performance;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import java.util.concurrent.atomic.AtomicLongArray;

/** Opt-in aggregate timings only. Never accepts editor, clipboard or query strings. */
public final class ImePerformanceMetrics {
    public static final String PREFERENCE = "pref_ime_performance_diagnostics";
    public enum Event { BLUR_SCHEDULE, BLUR_APPLY, BLUR_CLEAR, THEME_OPACITY,
        COALESCED_APPLY, UNCHANGED_BLUR, CANDIDATE_LIST, CANDIDATES, FAVORITES, CLIPBOARD_BIND, SEARCH_SNAPSHOT }
    private static final AtomicLongArray counts = new AtomicLongArray(Event.values().length);
    private static final AtomicLongArray totals = new AtomicLongArray(Event.values().length);
    private static final AtomicLongArray maxima = new AtomicLongArray(Event.values().length);
    private static volatile boolean enabled;
    private ImePerformanceMetrics() {}
    public static boolean enabled() { return enabled; }
    public static void refresh(Context context) {
        try {
            SharedPreferences preferences = GboardPatchesSettings.preferences(context);
            enabled = Boolean.TRUE.equals(preferences.getAll().get(PREFERENCE));
        } catch (Throwable ignored) { enabled = false; }
    }
    public static long begin() { return enabled ? System.nanoTime() : 0; }
    public static void end(Event event, long start) {
        if (start == 0 || !enabled) return;
        int i=event.ordinal(); long elapsed=System.nanoTime()-start;
        counts.incrementAndGet(i); totals.addAndGet(i, elapsed);
        long old;
        do { old=maxima.get(i); if (old>=elapsed) break; } while (!maxima.compareAndSet(i, old, elapsed));
    }
    public static void count(Event event) { if (enabled) counts.incrementAndGet(event.ordinal()); }
    public static void reportAndReset() {
        if (!enabled) return;
        StringBuilder result=new StringBuilder("IME timings (ns):");
        for (Event event:Event.values()) {
            int i=event.ordinal(); long count=counts.getAndSet(i,0), total=totals.getAndSet(i,0), max=maxima.getAndSet(i,0);
            if (count>0) result.append(' ').append(event.name()).append("[n=").append(count)
                    .append(",total=").append(total).append(",max=").append(max).append(']');
        }
        Log.i("GboardPatchesPerf",result.toString());
    }
}
