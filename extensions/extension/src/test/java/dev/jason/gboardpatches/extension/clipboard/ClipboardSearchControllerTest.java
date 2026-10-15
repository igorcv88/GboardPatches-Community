package dev.jason.gboardpatches.extension.clipboard;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static dev.jason.gboardpatches.extension.clipboard.ClipboardSearchController.*;

public class ClipboardSearchControllerTest {
    @Test public void zeroOneHundredFiveHundredClipsKeepOrderIdsAndSensitiveHidden() throws Exception {
        for (int count : new int[] {0,1,100,500}) {
            try (ClipboardSearchController c = new ClipboardSearchController(Runnable::run)) {
                List<Projection> input = new ArrayList<>(); List<Long> expected = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    input.add(new Projection(i, i%2==0?Section.RECENT:Section.PINNED, "duplicado\nhello", i==3));
                    if (i!=3) expected.add((long)i);
                }
                c.replaceSnapshot(input); CountDownLatch latch = new CountDownLatch(1);
                List<Long>[] actual = new List[1];
                c.search("HELLO", ids -> { actual[0] = ids; latch.countDown(); });
                assertTrue(latch.await(5,TimeUnit.SECONDS)); assertEquals(expected,actual[0]);
            }
        }
    }
    @Test public void queryChangeSnapshotChangeAndCloseInvalidateQueuedUiResults() throws Exception {
        BlockingQueue<Runnable> ui = new LinkedBlockingQueue<>();
        ClipboardSearchController c = new ClipboardSearchController(ui::add);
        c.replaceSnapshot(List.of(new Projection(42,Section.RECENT,"hello",false)));
        c.search("hello", ids -> fail("stale query published"));
        Runnable stale = ui.poll(5,TimeUnit.SECONDS); assertNotNull(stale);
        c.search("absent", ids -> assertTrue(ids.isEmpty())); stale.run();
        Runnable current = ui.poll(5,TimeUnit.SECONDS); assertNotNull(current); current.run();
        c.search("hello", ids -> fail("closed panel published"));
        Runnable closed = ui.poll(5,TimeUnit.SECONDS); assertNotNull(closed); c.close(); closed.run();
        c.search("hello", ids -> fail("search after close"));
    }
    @Test public void clearingQueryRestoresMaskedAndNonTextStockIds() throws Exception {
        try (ClipboardSearchController c = new ClipboardSearchController(Runnable::run)) {
            c.replaceSnapshot(List.of(new Projection(1,Section.PINNED,"private",true),
                    new Projection(2,Section.RECENT,null,false)));
            CountDownLatch latch = new CountDownLatch(1);
            List<Long>[] actual = new List[1];
            c.search("", ids -> { actual[0]=ids; latch.countDown(); });
            assertTrue(latch.await(5,TimeUnit.SECONDS)); assertEquals(List.of(1L,2L),actual[0]);
        }
    }
    @Test public void snapshotRefreshInvalidatesPendingResultsAndSensitiveTextIsDiscarded() throws Exception {
        Projection sensitive = new Projection(1,Section.PINNED,"private",true); assertNull(sensitive.text);
        BlockingQueue<Runnable> ui = new LinkedBlockingQueue<>();
        try (ClipboardSearchController c = new ClipboardSearchController(ui::add)) {
            c.replaceSnapshot(List.of(new Projection(1,Section.RECENT,"hello",false)));
            c.search("hello", ids -> fail("old snapshot"));
            Runnable old = ui.poll(5,TimeUnit.SECONDS); assertNotNull(old);
            c.replaceSnapshot(List.of()); old.run();
        }
    }
}
