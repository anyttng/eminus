package com.eminus.cell.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

final class ReleasedCells {
    static final int MAX_PARKED = 64;

    private final Long2ObjectLinkedOpenHashMap<CellHandle> parked = new Long2ObjectLinkedOpenHashMap<>();

    void park(CellHandle handle) {
        parked.putAndMoveToLast(handle.key(), handle);
        if (parked.size() > MAX_PARKED) {
            parked.removeFirst();
        }
    }

    CellHandle take(long key) {
        return parked.remove(key);
    }

    int size() {
        return parked.size();
    }
}
