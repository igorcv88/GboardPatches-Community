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
