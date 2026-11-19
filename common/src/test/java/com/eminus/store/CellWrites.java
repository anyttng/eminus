package com.eminus.store;

import com.eminus.cell.Cell;

public final class CellWrites {
    public static void write(CellStore store, Cell cell) {
        store.write(cell.key(), cell.occupancy(), CellRecord.encode(cell));
    }

    private CellWrites() {
    }
}
