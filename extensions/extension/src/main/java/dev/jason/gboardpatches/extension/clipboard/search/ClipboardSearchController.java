package dev.jason.gboardpatches.extension.clipboard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Session-scoped worker. No Android views/models, database writes or persistent query state. */
public final class ClipboardSearchController implements AutoCloseable {
    public enum Section { RECENT, PINNED, SPECIAL }
    public static final class Projection {
        public final long id;
        public final Section section;
        public final String text;
        public final boolean sensitive;
        public Projection(long id, Section section, String text, boolean sensitive) {
            this.id = id;
            this.section = section;
            // Never retain a sensitive projection in the search worker.
            this.text = sensitive ? null : text;
            this.sensitive = sensitive;
        }
    }
    private final ScheduledThreadPoolExecutor worker;
    private final Executor ui;
    private final AtomicLong generation = new AtomicLong();
    private List<Projection> snapshot = List.of();
    private ScheduledFuture<?> pending;
    private boolean closed;

    /** Production panel adapters must use this factory; tests may inject an executor. */
    public static ClipboardSearchController forImeMainThread() {
        android.os.Looper main = android.os.Looper.getMainLooper();
        if (android.os.Looper.myLooper() != main) {
            throw new IllegalStateException("Clipboard panel must attach on the IME main thread");
        }
        android.os.Handler handler = new android.os.Handler(main);
        return new ClipboardSearchController(command -> handler.post(command));
    }

    public ClipboardSearchController(Executor ui) {
        this.ui = ui;
        worker = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "gboard-clipboard-search");
            thread.setDaemon(true);
            return thread;
        });
        worker.setRemoveOnCancelPolicy(true);
    }

    /** Supply immutable projections on panel load/refresh, preserving stock order and IDs. */
    public synchronized void replaceSnapshot(List<Projection> projections) {
        if (closed) return;
        generation.incrementAndGet();
        if (pending != null) pending.cancel(true);
        snapshot = List.copyOf(projections);
    }

    /** The UI callback receives original IDs; the view adapter must revalidate before applying. */
    public synchronized void search(String query, Consumer<List<Long>> result) {
        if (closed) return;
        long request = generation.incrementAndGet();
        if (pending != null) pending.cancel(true);
        List<Projection> input = snapshot;
        // Query normalization also belongs off the IME thread.
        String bounded = query == null ? "" : query.substring(0,
                Math.min(query.length(), ClipboardSearchMatcher.MAX_QUERY_LENGTH));
        pending = worker.schedule(() -> {
            ClipboardSearchMatcher matcher = new ClipboardSearchMatcher(bounded);
            List<Long> ids = new ArrayList<>();
            for (Projection item : input) {
                if (generation.get() != request || Thread.currentThread().isInterrupted()) return;
                if (matcher.empty() || !item.sensitive && matcher.matches(item.text,
                        () -> generation.get() != request)) ids.add(item.id);
            }
            if (generation.get() != request) return;
            List<Long> immutable = List.copyOf(ids);
            ui.execute(() -> { if (generation.get() == request) result.accept(immutable); });
        }, 150, TimeUnit.MILLISECONDS);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        generation.incrementAndGet();
        snapshot = List.of();
        if (pending != null) pending.cancel(true);
        pending = null;
        worker.shutdownNow();
    }
}
