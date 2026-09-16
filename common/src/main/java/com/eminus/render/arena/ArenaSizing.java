package com.eminus.render.arena;

import com.eminus.cell.DetailLevel;

public final class ArenaSizing {
    public static final int QUAD_BYTES = Long.BYTES;
    public static final long BLOCK_BYTES = (long) ArenaAllocator.QUADS_PER_BLOCK * QUAD_BYTES;
    public static final long MIN_BYTES = 32L * 1024 * 1024;
    public static final long MAX_BYTES = 512L * 1024 * 1024;
    public static final int BUDGETED_QUADS_PER_CELL = 2600;
    public static final int DEVICE_SHARE = 4;
    public static final long REFUSED = 0L;

    private ArenaSizing() {
    }

    public static long wanted(int farRenderCells, int subdivisionPixels, float focalPixels, int lowestLevel) {
        long quads = 0;

        for (int level = Math.max(lowestLevel, DetailLevel.MIN); level <= DetailLevel.MAX; level++) {
            quads += (long) ArenaDemand.columns(level, focalPixels, subdivisionPixels, farRenderCells)
                    * ArenaDemand.cellsPerColumn(level) * BUDGETED_QUADS_PER_CELL;
        }

        return Math.clamp(quads * QUAD_BYTES, MIN_BYTES, MAX_BYTES);
    }

    public static long fitted(long wanted, long maxMemoryAllocationSize) {
        long allowed = Math.min(wanted, maxMemoryAllocationSize / DEVICE_SHARE);
        long aligned = allowed - Math.floorMod(allowed, BLOCK_BYTES);
        return aligned < MIN_BYTES ? REFUSED : aligned;
    }

    public static int blocks(long bytes) {
        return (int) (bytes / BLOCK_BYTES);
    }
}
