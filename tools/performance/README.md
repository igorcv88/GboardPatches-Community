# IME performance validation

Baseline: main `8d8c6a52ad7788450061a3c95bd27dd907fe59f5`, release 3.12.1.
Target: original Gboard 18.0.3.954559732, versionCode 175940518, arm64.

The supplied capture reports 132 frames, 85 janky (64.39%), P95 81 ms.
The parser finds 120 CSV rows, including 89 ordinary (`Flags=0`) rows. QueueBufferDuration
P95 is 30.6351 ms; FrameStartTime→SyncStart P95 is 28.6563 ms. These intervals include
waiting/backpressure. The GPU 4950 ms bucket is anomalous; it is not evidence of a
4.95 second GPU execution. Aggregate percentiles and CSV-derived intervals measure
different populations and must not be substituted for each other.

A Robolectric experiment invoked the actual baseline and modified `verifyAndApply`
methods 100 times on an unchanged bounded window:

| Operation | Baseline | Modified |
|---|---:|---:|
| Nonzero blur radius writes | 100 | 1 |
| Clear-to-zero writes | 99 | 0 |
| Window background replacements | 199 | 1 |

This is an operation count, not a device latency/jank result. Regression tests retain
assertions for repeated schedules and cancellation across disable. Pending applies
now coalesce once per frame. Layout/gravity mutations are conditional, unconditional
root requestLayout is removed, and identical bounded blur state is retained. No blur
radius reduction or surface cropping was used. The local MIUI fallback still needs
device profiling; it is not assumed active on Samsung.

## Device comparison still required

Control application, theme, text, refresh rate, thermal state, and repetitions.
Reset `dumpsys gfxinfo dev.jason.com.google.android.inputmethod.latin reset` before each
capture. Repeat at least five times per scenario, retaining raw framestats and
Perfetto FrameTimeline/SurfaceFlinger traces:

1. Current theme, blur enabled.
2. Same theme, blur disabled.
3. Stock opaque theme, blur disabled.
4. Scenario 2 with contextual toolbar disabled.

Compare baseline APK and candidate APK under identical conditions. Do not combine
pre-reset and post-reset totals. Run `python3 tools/analyze_ime_framestats.py FILE`
and optionally `--compare OTHER_FILE` only for controlled captures.

Enable Performance diagnostics in Frosted Glass settings only while measuring.
Diagnostics default off and emit aggregate counts, total nanoseconds and maxima
when the IME closes. No editor/query/clipboard values are accepted. These counters
do not measure GPU/SF work, buffer allocation, or end-to-end frame latency.

Physical gates: rapid editor switches, floating/landscape, dark/light, theme changes
while visible, and blur capability transitions. Targets (<5–10% jank, P95 <16.7 ms)
are aspirational and have NOT been demonstrated by host tests.
