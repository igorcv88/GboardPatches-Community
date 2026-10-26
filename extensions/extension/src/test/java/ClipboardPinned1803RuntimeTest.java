import android.content.Context;
import android.os.Looper;
import dev.jason.gboardpatches.extension.clipboard.ClipboardPinned1803Runtime;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE)
public class ClipboardPinned1803RuntimeTest {
    private fjk adapter() {
        return new fjk(RuntimeEnvironment.getApplication(), new ArrayList<>(List.of(
                fih.a, "recent1", "recent2", fih.b, "pinned1", "pinned2", fih.c)), 2, 2);
    }
    @Test public void bindingMenuSwipeAndSelectionUseTheCanonicalItemPosition() {
        fjk adapter = adapter();
        List<?> nativeItems = List.copyOf(adapter.n);
        kr holder = new kr(); holder.position = 1;
        assertEquals(4, ClipboardPinned1803Runtime.bindPosition(adapter, holder, 1));
        assertEquals(4, ClipboardPinned1803Runtime.holderPosition(holder));
        assertEquals("pinned1", adapter.n.get(ClipboardPinned1803Runtime.holderPosition(holder)));
        assertEquals(1, ClipboardPinned1803Runtime.visualPosition(adapter, 4));
        assertEquals(4, ClipboardPinned1803Runtime.visualPosition(adapter, 1));
        holder.position = -1;
        assertEquals(-1, ClipboardPinned1803Runtime.holderPosition(holder));
        assertEquals(nativeItems, adapter.n);
    }
    @Test public void aQueuedTapKeepsTheBoundModelWhenNativePositionsChangeBeforeRebind() {
        fjk adapter = adapter(); kr holder = new kr(); holder.position = 1;
        ClipboardPinned1803Runtime.bindPosition(adapter, holder, 1);
        adapter.n.add(1, "new recent");
        ClipboardPinned1803Runtime.changed(adapter);
        assertEquals(5, ClipboardPinned1803Runtime.holderPosition(holder));
        assertEquals("pinned1", adapter.n.get(ClipboardPinned1803Runtime.holderPosition(holder)));
        adapter.n.remove("pinned1");
        ClipboardPinned1803Runtime.changed(adapter);
        assertEquals(-1, ClipboardPinned1803Runtime.holderPosition(holder));
    }

    @Test public void movesAndDeletesInvalidateTheProjectionAndCoalesceNotifications() {
        fjk adapter = adapter();
        assertEquals(4, ClipboardPinned1803Runtime.nativePosition(adapter, 1));
        Object moved = adapter.n.remove(4); adapter.n.add(1, moved); // Native unpin.
        ClipboardPinned1803Runtime.notifyMoved(adapter, 4, 1);
        ClipboardPinned1803Runtime.notifyChanged(adapter, 1);
        assertEquals(5, ClipboardPinned1803Runtime.nativePosition(adapter, 1));
        assertEquals("pinned2", adapter.n.get(ClipboardPinned1803Runtime.nativePosition(adapter, 1)));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, adapter.fullRefreshes);
        adapter.n.remove(5);
        ClipboardPinned1803Runtime.notifyRemoved(adapter, 5);
        assertEquals(1, ClipboardPinned1803Runtime.nativePosition(adapter, 1));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, adapter.fullRefreshes);
        assertTrue(adapter.removed.isEmpty()); // No canonical animation sent to displayed indices.
    }
    @Test public void sharedSyntheticListenersLeaveOtherAdaptersNotificationsUnchanged() {
        jt ordinary = new jt();
        ClipboardPinned1803Runtime.notifyRemoved(ordinary, 3);
        assertEquals(List.of(3), ordinary.removed);
        assertEquals(0, ordinary.fullRefreshes);
    }
    @Test public void oldAndNewPanelsHaveIndependentPositionTranslations() {
        fjk first = adapter(), second = adapter();
        second.n.remove(4); second.n.remove(4);
        kr old = new kr(); old.position = 1;
        ClipboardPinned1803Runtime.bindPosition(first, old, 1);
        assertEquals(1, ClipboardPinned1803Runtime.nativePosition(second, 1));
        assertEquals(4, ClipboardPinned1803Runtime.holderPosition(old));
    }
}
