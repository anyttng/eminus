package com.eminus.render.tree;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

final class PendingColumns {
    private static final int COLUMN_BLOCKS = DetailLevel.blocksPerCell(DetailLevel.MIN);
    private static final int NONE = Integer.MIN_VALUE;
    private static final long LOW_MASK = 0xFFFF_FFFFL;

    private final Long2IntOpenHashMap highestBottom = new Long2IntOpenHashMap();

    PendingColumns() {
        highestBottom.defaultReturnValue(NONE);
    }

    void clear() {
        highestBottom.clear();
    }

    void waiting(CellFrame frame, long key) {
        int bottom = frame.originYOf(key);
        int side = DetailLevel.blocksPerCell(CellKey.level(key));
        int firstX = Math.floorDiv(CellFrame.originXOf(key), COLUMN_BLOCKS);
        int firstZ = Math.floorDiv(CellFrame.originZOf(key), COLUMN_BLOCKS);
        int columns = side / COLUMN_BLOCKS;

        for (int x = firstX; x < firstX + columns; x++) {
            for (int z = firstZ; z < firstZ + columns; z++) {
                long column = column(x, z);
                if (bottom > highestBottom.get(column)) {
                    highestBottom.put(column, bottom);
                }
            }
        }
    }

    boolean under(CellFrame frame, long key) {
        if (highestBottom.isEmpty()) {
            return false;
        }

        int top = frame.originYOf(key) + DetailLevel.blocksPerCell(CellKey.level(key));
        int side = DetailLevel.blocksPerCell(CellKey.level(key));
        int firstX = Math.floorDiv(CellFrame.originXOf(key), COLUMN_BLOCKS);
        int firstZ = Math.floorDiv(CellFrame.originZOf(key), COLUMN_BLOCKS);
        int columns = side / COLUMN_BLOCKS;

        for (int x = firstX; x < firstX + columns; x++) {
            for (int z = firstZ; z < firstZ + columns; z++) {
                if (highestBottom.get(column(x, z)) >= top) {
                    return true;
                }
            }
        }

        return false;
    }

    private static long column(int x, int z) {
        return ((long) x << Integer.SIZE) | (z & LOW_MASK);
    }
}
