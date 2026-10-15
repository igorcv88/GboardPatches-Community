package dev.jason.gboardpatches.extension.clipboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure presentation projection. The same original model objects reach stock callbacks. */
public final class ClipboardSearchPresentation {
    public static final class Row<T> {
        public final long id;
        public final ClipboardSearchController.Section section;
        public final T model;
        public final boolean header;
        public Row(long id, ClipboardSearchController.Section section, T model, boolean header) {
            this.id = id;
            this.section = section;
            this.model = model;
            this.header = header;
        }
    }
    public static final class Result<T> {
        public final List<T> items;
        public final int recentCount, pinnedCount;
        private Result(List<T> items, int recentCount, int pinnedCount) {
            this.items = List.copyOf(items);
            this.recentCount = recentCount;
            this.pinnedCount = pinnedCount;
        }
    }

    /** Run on the UI/model owner thread with its current snapshot, never the search worker. */
    public static <T> Result<T> filter(List<Row<T>> stock, List<Long> matchingIds) {
        Set<Long> ids = new HashSet<>(matchingIds);
        Set<ClipboardSearchController.Section> sections = new HashSet<>();
        for (Row<T> row : stock) if (!row.header && ids.contains(row.id)) sections.add(row.section);
        List<T> result = new ArrayList<>();
        int recent = 0, pinned = 0;
        for (Row<T> row : stock) {
            if (row.header) {
                if (sections.contains(row.section)) result.add(row.model);
            } else if (ids.contains(row.id)) {
                result.add(row.model);
                if (row.section == ClipboardSearchController.Section.RECENT) recent++;
                if (row.section == ClipboardSearchController.Section.PINNED) pinned++;
            }
        }
        return new Result<>(result, recent, pinned);
    }
    private ClipboardSearchPresentation() {}
}
