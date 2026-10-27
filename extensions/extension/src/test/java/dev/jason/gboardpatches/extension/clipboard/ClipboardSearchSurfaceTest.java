package dev.jason.gboardpatches.extension.clipboard;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class ClipboardSearchSurfaceTest {
    private static final ClipboardSearchController.Section R = ClipboardSearchController.Section.RECENT;
    private static final ClipboardSearchController.Section P = ClipboardSearchController.Section.PINNED;

    private static void settle() throws Exception {
        for (int i = 0; i < 25; i++) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20); }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void fieldQueryFiltersFullTextInNativeOrderAndPickReportsTheClip() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        EditText field = new EditText(activity);
        List<ClipboardSearchSurface.Clip> picked = new ArrayList<>();
        int[] closes = {0};
        ClipboardSearchSurface surface = new ClipboardSearchSurface(activity, field, 600,
                ClipboardSearchController.forImeMainThread(), new ClipboardSearchSurface.Listener() {
                    public void onPick(ClipboardSearchSurface.Clip clip) { picked.add(clip); }
                    public void onClose() { closes[0]++; }
                });
        activity.setContentView(surface);
        ClipboardSearchSurface.Clip first = new ClipboardSearchSurface.Clip(1, R, "Ação rápida");
        ClipboardSearchSurface.Clip other = new ClipboardSearchSurface.Clip(2, R, "nothing here");
        ClipboardSearchSurface.Clip deep = new ClipboardSearchSurface.Clip(3, P, "x".repeat(5000) + " acao final");
        surface.setClips(List.of(first, other, deep));
        assertEquals(List.of(first, other, deep), surface.visibleClips());

        field.setText("ACAO");
        settle();
        assertEquals(List.of(first, deep), surface.visibleClips());
        assertEquals(View.GONE, surface.empty.getVisibility());

        field.setText("zzz");
        settle();
        assertTrue(surface.visibleClips().isEmpty());
        assertEquals(View.VISIBLE, surface.empty.getVisibility());

        field.setText("");
        settle();
        assertEquals(3, surface.visibleClips().size());

        surface.results.performItemClick(null, 2, 3);
        assertEquals(List.of(deep), picked);
        surface.close.performClick();
        assertEquals(1, closes[0]);
    }

    private static void touch(ClipboardSearchSurface surface, long start, long at, int action, float x, float y) {
        android.view.MotionEvent event = android.view.MotionEvent.obtain(start, at, action, x, y, 0);
        surface.dispatchTouchEvent(event);
        event.recycle();
    }

    @Test public void dispatchedFingerGesturesPickScrollAndCloseWithoutPhantomClicks() {
        org.robolectric.android.controller.ActivityController<Activity> controller =
                Robolectric.buildActivity(Activity.class).setup().visible();
        Activity activity = controller.get();
        List<ClipboardSearchSurface.Clip> picked = new ArrayList<>();
        int[] closes = {0};
        ClipboardSearchSurface surface = new ClipboardSearchSurface(activity, new EditText(activity), 400,
                ClipboardSearchController.forImeMainThread(), new ClipboardSearchSurface.Listener() {
                    public void onPick(ClipboardSearchSurface.Clip clip) { picked.add(clip); }
                    public void onClose() { closes[0]++; }
                });
        List<ClipboardSearchSurface.Clip> clips = new ArrayList<>();
        for (int i = 0; i < 80; i++) clips.add(new ClipboardSearchSurface.Clip(i, R, "test row " + i));
        surface.setClips(clips);
        activity.setContentView(surface);
        surface.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        surface.layout(0, 0, 600, 400);
        // AbsListView.WindowRunnable rejects clicks when the test window lacks focus.
        controller.windowFocusChanged(true);
        assertTrue(surface.hasWindowFocus());
        View row = surface.results.getChildAt(0);
        assertNotNull(row);
        float x = 300, y = surface.results.getTop() + row.getTop() + row.getHeight() / 2f;
        touch(surface, 0, 0, 0, x, y);
        touch(surface, 0, 50, 1, x, y);
        // AbsListView delays PerformClick by the framework pressed-state duration.
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
        assertEquals(List.of(clips.get(0)), picked);
        int before = surface.results.getChildAt(0).getTop();
        float bottom = surface.results.getBottom() - 20;
        touch(surface, 100, 100, 0, x, bottom);
        for (int step = 1; step <= 8; step++) touch(surface, 100, 100 + step * 30, 2, x, bottom - step * 20);
        touch(surface, 100, 370, 1, x, bottom - 160);
        assertTrue(surface.results.getFirstVisiblePosition() > 0 || surface.results.getChildAt(0).getTop() < before);
        assertEquals(1, picked.size()); // A drag must not paste.
        touch(surface, 400, 400, 0, -20, -20);
        touch(surface, 400, 450, 1, -20, -20);
        assertEquals(1, picked.size()); assertEquals(0, closes[0]);
        android.graphics.Rect back = new android.graphics.Rect(0, 0, surface.close.getWidth(), surface.close.getHeight());
        surface.offsetDescendantRectToMyCoords(surface.close, back);
        touch(surface, 500, 500, 0, back.centerX(), back.centerY());
        touch(surface, 500, 550, 1, back.centerX(), back.centerY());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, closes[0]);
        surface.close();
    }

    @Test public void visibleQueryIsBoundedLikeTheSearchedQuery() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        EditText field = new EditText(activity);
        new ClipboardSearchSurface(activity, field, 600, ClipboardSearchController.forImeMainThread(),
                new ClipboardSearchSurface.Listener() {
                    public void onPick(ClipboardSearchSurface.Clip clip) { }
                    public void onClose() { }
                });
        field.setText("a".repeat(ClipboardSearchMatcher.MAX_QUERY_LENGTH + 50));
        assertEquals(ClipboardSearchMatcher.MAX_QUERY_LENGTH, field.length());
    }

    @Test public void deleteNeedsLongPressThenAConfirmingTapAndIsOffUnlessEnabled() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        EditText field = new EditText(activity);
        List<ClipboardSearchSurface.Clip> deleted = new ArrayList<>(), picked = new ArrayList<>();
        ClipboardSearchSurface surface = new ClipboardSearchSurface(activity, field, 600,
                ClipboardSearchController.forImeMainThread(), new ClipboardSearchSurface.Listener() {
                    public void onPick(ClipboardSearchSurface.Clip clip) { picked.add(clip); }
                    public void onClose() { }
                    public void onDelete(ClipboardSearchSurface.Clip clip) { deleted.add(clip); }
                });
        activity.setContentView(surface);
        ClipboardSearchSurface.Clip a = new ClipboardSearchSurface.Clip(1, R, "alpha");
        ClipboardSearchSurface.Clip b = new ClipboardSearchSurface.Clip(2, R, "beta");
        surface.setClips(List.of(a, b));
        surface.results.getOnItemLongClickListener().onItemLongClick(surface.results, null, 0, 1);
        assertEquals(Long.MIN_VALUE, surface.pendingDelete()); // Disabled by default.

        surface.setDeleteEnabled(true);
        surface.results.getOnItemLongClickListener().onItemLongClick(surface.results, null, 0, 1);
        assertEquals(1L, surface.pendingDelete());
        surface.results.performItemClick(null, 1, 2); // Another row only cancels.
        assertTrue(deleted.isEmpty()); assertTrue(picked.isEmpty());
        assertEquals(Long.MIN_VALUE, surface.pendingDelete());

        surface.results.getOnItemLongClickListener().onItemLongClick(surface.results, null, 0, 1);
        surface.results.performItemClick(null, 0, 1);
        assertEquals(List.of(a), deleted);
        assertTrue(picked.isEmpty());

        surface.remove(1);
        assertEquals(List.of(b), surface.visibleClips());
    }

    @Test public void deletingAFilteredClipNeverExposesNonmatchingResultsDuringDebounce() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        EditText field = new EditText(activity);
        List<ClipboardSearchSurface.Clip> picked = new ArrayList<>();
        ClipboardSearchSurface surface = new ClipboardSearchSurface(activity, field, 600,
                ClipboardSearchController.forImeMainThread(), new ClipboardSearchSurface.Listener() {
                    public void onPick(ClipboardSearchSurface.Clip clip) { picked.add(clip); }
                    public void onClose() { }
                });
        activity.setContentView(surface);
        ClipboardSearchSurface.Clip alpha = new ClipboardSearchSurface.Clip(1, R, "alpha");
        ClipboardSearchSurface.Clip beta = new ClipboardSearchSurface.Clip(2, R, "beta");
        ClipboardSearchSurface.Clip alphabet = new ClipboardSearchSurface.Clip(3, P, "alphabet");
        surface.setClips(List.of(alpha, beta, alphabet));
        field.setText("alpha");
        settle();
        assertEquals(List.of(alpha, alphabet), surface.visibleClips());

        // Deletion must be safe immediately, before the worker's 150 ms debounce.
        surface.remove(1);
        assertEquals(List.of(alphabet), surface.visibleClips());
        surface.results.performItemClick(null, 0, alphabet.id);
        assertEquals(List.of(alphabet), picked);
        settle();
        assertEquals(List.of(alphabet), surface.visibleClips());

        // An item changing text under the same ID must not retain its old match.
        ClipboardSearchSurface.Clip changed = new ClipboardSearchSurface.Clip(3, P, "gamma");
        surface.setClips(List.of(beta, changed));
        assertTrue(surface.visibleClips().isEmpty());
        settle();
        assertTrue(surface.visibleClips().isEmpty());

        // If the typed query is newer than the displayed results, do not reuse old rows.
        field.setText("beta");
        surface.setClips(List.of(beta, changed));
        assertTrue(surface.visibleClips().isEmpty());
        settle();
        assertEquals(List.of(beta), surface.visibleClips());
        surface.close();
    }

    @Test public void closeClearsQueryDetachesFieldAndIgnoresLateClicks() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        EditText field = new EditText(activity);
        int[] events = {0};
        ClipboardSearchSurface surface = new ClipboardSearchSurface(activity, field, 600,
                ClipboardSearchController.forImeMainThread(), new ClipboardSearchSurface.Listener() {
                    public void onPick(ClipboardSearchSurface.Clip clip) { events[0]++; }
                    public void onClose() { events[0]++; }
                });
        surface.setClips(List.of(new ClipboardSearchSurface.Clip(1, R, "alpha")));
        field.setText("alp");
        surface.close();
        surface.close();
        assertEquals("", field.getText().toString());
        assertTrue(surface.visibleClips().isEmpty());
        field.setText("alpha");
        settle();
        assertTrue(surface.visibleClips().isEmpty());
        surface.close.performClick();
        assertEquals(0, events[0]);
    }
}
