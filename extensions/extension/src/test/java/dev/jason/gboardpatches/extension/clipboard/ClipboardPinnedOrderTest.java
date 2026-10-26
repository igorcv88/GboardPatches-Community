package dev.jason.gboardpatches.extension.clipboard;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClipboardPinnedOrderTest {
    private final Object recent = new Object(), pinned = new Object(), special = new Object();
    @Test public void pinnedSectionIsFirstAndOrderInsideEachSectionIsStable() {
        List<Object> items = List.of(recent, "r1", "r2", pinned, "p1", "p2", special, "s1");
        List<Object> original = new ArrayList<>(items);
        int[] indices = ClipboardPinnedOrder.indices(items, recent, pinned, special);
        assertArrayEquals(new int[]{3,4,5,0,1,2,6,7}, indices);
        assertEquals(original, items);
        for (int i = 0; i < items.size(); i++) {
            int count = 0; for (int index : indices) if (index == i) count++;
            assertEquals(1, count);
        }
    }
    @Test public void noPinsAndMalformedHeadersKeepNativeOrder() {
        assertArrayEquals(new int[]{0,1,2,3,4}, ClipboardPinnedOrder.indices(
                List.of(recent, "r1", pinned, special, "s1"), recent, pinned, special));
        assertArrayEquals(new int[]{0,1}, ClipboardPinnedOrder.indices(List.of("r", "p"), recent, pinned, special));
        assertArrayEquals(new int[0], ClipboardPinnedOrder.indices(List.of(), recent, pinned, special));
    }
    @Test public void pinUnpinAndDeleteRebuildWithoutMutatingTheNativeModel() {
        List<Object> items = new ArrayList<>(List.of(recent, "r1", "r2", pinned, special));
        assertArrayEquals(new int[]{0,1,2,3,4}, ClipboardPinnedOrder.indices(items, recent, pinned, special));
        items.remove("r2"); items.add(items.indexOf(special), "r2");
        assertArrayEquals(new int[]{2,3,0,1,4}, ClipboardPinnedOrder.indices(items, recent, pinned, special));
        items.remove("r2");
        assertArrayEquals(new int[]{0,1,2,3}, ClipboardPinnedOrder.indices(items, recent, pinned, special));
    }
}
