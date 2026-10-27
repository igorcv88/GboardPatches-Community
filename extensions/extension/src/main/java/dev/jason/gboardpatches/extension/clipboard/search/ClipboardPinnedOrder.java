package dev.jason.gboardpatches.extension.clipboard;

import java.util.List;

/** Permutation only: native Recent/Pinned/Special data, storage and pruning stay untouched. */
final class ClipboardPinnedOrder {
    static int[] indices(List<?> items, Object recent, Object pinned, Object special) {
        int size = items.size();
        int[] order = new int[size];
        int r = items.indexOf(recent), p = items.indexOf(pinned), s = items.indexOf(special);
        if (r != 0 || p <= 0 || s <= p || s >= size || s == p + 1) {
            for (int i = 0; i < size; i++) order[i] = i;
            return order;
        }
        int next = 0;
        for (int i = p; i < s; i++) order[next++] = i;
        for (int i = 0; i < p; i++) order[next++] = i;
        for (int i = s; i < size; i++) order[next++] = i;
        return order;
    }
}
