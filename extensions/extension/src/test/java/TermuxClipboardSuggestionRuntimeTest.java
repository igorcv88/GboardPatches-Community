import android.app.AlertDialog;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.BaseInputConnection;
import dev.jason.gboardpatches.extension.clipboard.TermuxClipboardSuggestion;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

/** Tests the production adapter with deliberately small native ABI doubles, not a real Gboard. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class TermuxClipboardSuggestionRuntimeTest {
    public static class Ime extends InputMethodService {
        EditorInfo info;
        final List<String> inserted = new ArrayList<>();
        InputConnection connection = new BaseInputConnection(new View(org.robolectric.RuntimeEnvironment.getApplication()), false) {
            @Override public boolean commitText(CharSequence text, int cursor) {
                inserted.add(text.toString()); return false; // An uncertain result must not cause retry.
            }
        };
        @Override public EditorInfo getCurrentInputEditorInfo() { return info; }
        @Override public InputConnection getCurrentInputConnection() { return connection; }
    }
    public static class Helper {
        public EditorInfo m;
        public List<?> k = List.of();
        public boolean o;
        public Object l;
        int stockClicks;
        public View y(Context context, fih clip, boolean sensitive, int layout, boolean only, ppe surface) {
            View view = new View(context);
            view.setMinimumWidth(100); view.setMinimumHeight(48);
            view.setOnClickListener(v -> stockClicks++);
            return view;
        }
    }
    private Ime ime;
    private Helper helper;
    @Before public void setUp() {
        TermuxClipboardSuggestion.suspend();
        qjr.latest = null; qjr.host = null; qjr.requests = 0; qjr.defer = false; qjq.clears = 0; npc.disabled = false; qqw.unlocked = true;
        ime = Robolectric.buildService(Ime.class).create().get();
        ime.info = new EditorInfo(); ime.info.packageName = "com.termux";
        helper = new Helper(); helper.m = ime.info;
    }
    @After public void tearDown() {
        TermuxClipboardSuggestion.closed(helper); TermuxClipboardSuggestion.suspend();
        if (ime != null) ime.onDestroy();
    }
    private fih copy(String text, boolean sensitive) {
        fih clip = new fih(1, new j$.time.Instant(System.currentTimeMillis()), 0, false, false, sensitive);
        clip.g.c = text; helper.k = List.of(clip); return clip;
    }
    private void open() {
        assertTrue(TermuxClipboardSuggestion.handle(helper));
        TermuxClipboardSuggestion.start(ime);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void copyBeforeOpeningShowsNativeChipAndCommitsOnceWithUnicode() {
        copy("olá 🐈‍⬛ 日本語", false); open();
        assertNotNull(qjr.latest);
        View chip = qjr.latest.views.get(0);
        chip.performClick(); chip.performClick();
        assertEquals(List.of("olá 🐈‍⬛ 日本語"), ime.inserted);
        assertEquals(0, helper.stockClicks);
        TermuxClipboardSuggestion.handle(helper);
        assertEquals(1, ime.inserted.size());
    }
    @Test public void newCopyReplacesOldSuggestionAndInvalidatesItsListener() {
        copy("old", false); open(); View old = qjr.latest.views.get(0);
        copy("new", false); TermuxClipboardSuggestion.handle(helper);
        View latest = qjr.latest.views.get(0);
        old.performClick(); assertTrue(ime.inserted.isEmpty());
        latest.performClick(); assertEquals(List.of("new"), ime.inserted);
    }
    @Test public void leavingAndReturningReleasesOldChipAndRestoresLatestCopy() {
        copy("return", false); open(); View old = qjr.latest.views.get(0);
        TermuxClipboardSuggestion.suspend(); old.performClick();
        assertTrue(ime.inserted.isEmpty());
        TermuxClipboardSuggestion.start(ime); Shadows.shadowOf(Looper.getMainLooper()).idle();
        qjr.latest.views.get(0).performClick(); assertEquals(List.of("return"), ime.inserted);
    }
    @Test public void privateSensitiveEmptyAndLockedSessionsNeverPublish() {
        copy("secret", true); open(); assertNull(qjr.latest);
        copy("ordinary", false); helper.o = true;
        TermuxClipboardSuggestion.handle(helper); assertNull(qjr.latest);
        helper.o = false; ime.info.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        TermuxClipboardSuggestion.handle(helper); assertNull(qjr.latest);
        ime.info.imeOptions = 0; qqw.unlocked = false;
        TermuxClipboardSuggestion.handle(helper); assertNull(qjr.latest);
        qqw.unlocked = true; npc.disabled = true;
        TermuxClipboardSuggestion.handle(helper); assertNull(qjr.latest);
        npc.disabled = false; copy("", false);
        TermuxClipboardSuggestion.handle(helper); assertNull(qjr.latest);
    }
    @Test public void acceptsModuleEditorInfoCopyButRejectsAnotherField() {
        EditorInfo moduleInfo = new EditorInfo(); moduleInfo.packageName = "com.termux";
        helper.m = moduleInfo;
        copy("copied info", false); open(); assertNotNull(qjr.latest);
        ime.info.fieldId = 99; qjr.latest.views.get(0).performClick();
        assertTrue(ime.inserted.isEmpty());
    }
    public static class ThemeModule {
        final Context context;
        ThemeModule(Context context) { this.context = context; }
        public Context a() { return context; }
    }
    @Test public void nativeChipUsesTheActiveKeyboardThemeContext() {
        Context themed = new android.view.ContextThemeWrapper(ime, android.R.style.Theme_Material);
        helper.l = new ThemeModule(themed);
        copy("theme", false); open();
        assertSame(themed, qjr.latest.views.get(0).getContext());
    }
    @Test public void ordinaryEditorsKeepStockPathAndConnection() {
        copy("ordinary", false); ime.info.packageName = "ordinary.editor";
        InputConnection original = ime.connection;
        assertFalse(TermuxClipboardSuggestion.handle(helper));
        TermuxClipboardSuggestion.start(ime); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull(qjr.latest); assertSame(original, ime.connection);
        assertFalse(TermuxClipboardSuggestion.handle(new Object()));
    }
    @Test public void multilineTapNeverCommitsWithoutConfirmationWindow() {
        copy("echo one\necho two\n", false); open();
        qjr.latest.views.get(0).performClick();
        assertTrue(ime.inserted.isEmpty()); // Detached chip cannot open an attached confirmation.
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog == null || !dialog.isShowing());
    }
    @Test public void multilineRequiresSeparateConfirmationAndCommitsExactlyOnce() {
        android.app.Activity activity = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        qjr.host = new android.widget.FrameLayout(activity); activity.setContentView(qjr.host);
        copy("echo one\necho two\n", false); open();
        View view = qjr.latest.views.get(0); view.performClick(); view.performClick();
        assertTrue(ime.inserted.isEmpty());
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog); assertTrue(dialog.isShowing());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of("echo one\necho two\n"), ime.inserted);
        activity.finish();
    }
    @Test public void changedClipboardCancelsPendingMultilineConfirmation() {
        android.app.Activity activity = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        qjr.host = new android.widget.FrameLayout(activity); activity.setContentView(qjr.host);
        copy("first\nsecond", false); open(); qjr.latest.views.get(0).performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        copy("new", false); TermuxClipboardSuggestion.handle(helper);
        assertFalse(dialog.isShowing());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(ime.inserted.isEmpty());
        qjr.latest.views.get(0).performClick(); assertEquals(List.of("new"), ime.inserted);
        activity.finish();
    }
    @Test public void deferredRequestIsRetriedAfterActualHeaderInitialization() {
        copy("deferred", false); open();
        View requested = qjr.latest.views.get(0);
        assertNull(requested.getParent()); assertEquals(1, qjr.requests);
        android.app.Activity activity = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        qjr.host = new android.widget.FrameLayout(activity); activity.setContentView(qjr.host);
        TermuxClipboardSuggestion.headerPrepared(qjr.host, ppe.a);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertSame(requested, qjr.host.getChildAt(0));
        qjr.host.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        qjr.host.layout(0, 0, 400, 100);
        qjr.host.getViewTreeObserver().dispatchOnGlobalLayout();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        int attempts = qjr.requests;
        TermuxClipboardSuggestion.handle(helper);
        assertEquals(attempts, qjr.requests);
        requested.performClick(); assertEquals(List.of("deferred"), ime.inserted);
        activity.finish();
    }
    @Test public void attachedButFullyClippedChipIsNotConsideredVisible() throws Exception {
        android.app.Activity activity = Robolectric.buildActivity(android.app.Activity.class).setup().visible().get();
        qjr.host = new android.widget.FrameLayout(activity); activity.setContentView(qjr.host);
        copy("clipped", false); open();
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 400, 800);
        View requested = qjr.latest.views.get(0);
        // Robolectric does not drive the test IME window's visibility via WindowManager.
        // Set the host attachment's window flag explicitly for these geometry assertions.
        Object attachment = org.robolectric.util.ReflectionHelpers.getField(requested, "mAttachInfo");
        org.robolectric.util.ReflectionHelpers.setField(attachment, "mWindowVisibility", View.VISIBLE);
        qjr.host.layout(0, 0, 400, 0); requested.layout(0, 0, 100, 48);
        assertTrue(requested.isAttachedToWindow()); assertTrue(requested.isShown());
        java.lang.reflect.Method visible = TermuxClipboardSuggestion.class.getDeclaredMethod("actuallyVisible");
        visible.setAccessible(true); assertEquals(false, visible.invoke(null));
        qjr.host.layout(0, 0, 400, 100); requested.layout(0, 0, 100, 48);
        assertEquals(true, visible.invoke(null));
        qjr.host.setAlpha(0f); assertEquals(false, visible.invoke(null));
        activity.finish();
    }
    @Test public void permanentlyUnavailableHolderHasBoundedRetriesAndStopsAtSessionEnd() {
        android.app.Activity activity = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        qjr.host = new android.widget.FrameLayout(activity); activity.setContentView(qjr.host);
        qjr.defer = true;
        copy("never log this text", false); open();
        TermuxClipboardSuggestion.headerPrepared(qjr.host, ppe.a);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        for (int i = 0; i < 10; i++) {
            qjr.host.getViewTreeObserver().dispatchOnGlobalLayout();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertEquals(3, qjr.requests);
        assertNull(qjr.latest.views.get(0).getParent());
        TermuxClipboardSuggestion.suspend();
        qjr.host.getViewTreeObserver().dispatchOnGlobalLayout();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(3, qjr.requests); assertTrue(ime.inserted.isEmpty());
        for (org.robolectric.shadows.ShadowLog.LogItem log : org.robolectric.shadows.ShadowLog.getLogsForTag("GboardTermuxClip")) {
            assertFalse(log.msg.contains("never log this text"));
        }
        activity.finish();
    }
    @Test public void privacyChangeAfterRenderPreventsInsertion() {
        copy("visible", false); open(); View old = qjr.latest.views.get(0);
        helper.o = true; old.performClick(); assertTrue(ime.inserted.isEmpty());
        helper.o = false; ime.connection = null;
        old.performClick(); assertTrue(ime.inserted.isEmpty());
    }
}

enum ppe { HEADER; static final ppe a = HEADER; }
enum qjw { c }
class npc {
    static boolean disabled;
    public static boolean u(String prefix, String option, EditorInfo info) { return disabled; }
}
class qqw {
    static boolean unlocked = true;
    public static boolean e() { return unlocked; }
}
class qjv {
    final List<View> views;
    qjv(List<View> views) { this.views = views; }
}
class qju {
    private List<View> views;
    public void b(qjw category) { }
    public void d(String source) { }
    public void e(List<View> value) { views = value; }
    public qjv a() { return new qjv(views); }
}
class qjr {
    static qjv latest;
    static android.widget.FrameLayout host;
    static int requests;
    static boolean defer;
    public static void a(qjv value, pfz priority) {
        throw new AssertionError("Single-suggestion overload must not be used for Termux");
    }
    public static void c(java.util.Map<ppe, qjv> bySurface, pfz priority) {
        assertEquals(1, bySurface.size()); assertTrue(bySurface.containsKey(ppe.a));
        latest = bySurface.get(ppe.a); requests++;
        if (host != null && !defer) { host.removeAllViews(); host.addView(latest.views.get(0)); }
    }
}
class qjq {
    static int clears;
    public static void c(qjw category, boolean force) { clears++; }
}
