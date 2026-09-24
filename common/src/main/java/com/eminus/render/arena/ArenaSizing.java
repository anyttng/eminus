package com.eminus.render.arena;

import java.util.OptionalLong;

import com.eminus.cell.DetailLevel;
import com.eminus.render.far.DrawCommands;

public final class ArenaSizing {
    public static final int QUAD_BYTES = Long.BYTES;
    public static final long BLOCK_BYTES = (long) ArenaAllocator.QUADS_PER_BLOCK * QUAD_BYTES;
    public static final long MIN_BYTES = 32L * 1024 * 1024;
    public static final long UNREAD_TEXEL_BYTES = 2048L * 1024 * 1024;
    public static final long VERTEX_INDEX_BYTES = (1L << Integer.SIZE - 1) / DrawCommands.VERTICES_PER_QUAD * QUAD_BYTES;
    public static final int BLOCK_FILL_PERCENT = 95;
    public static final int DEVICE_SHARE = 4;
    public static final int FREE_MEMORY_SHARE = 2;
    public static final long REFUSED = 0L;

    private static final int[] BUDGETED_QUADS_PER_CELL = {6600, 9500, 11400, 7200, 5700};
    private static final long WHOLE_PERCENT = 100L;

    private ArenaSizing() {
    }

    public static int budgetedQuadsPerCell(int level) {
        return BUDGETED_QUADS_PER_CELL[level - DetailLevel.MIN];
    }

    public static long wanted(int farRenderCells, int subdivisionPixels, float focalPixels, int lowestLevel) {
        long quads = 0;

        for (int level = Math.max(lowestLevel, DetailLevel.MIN); level <= DetailLevel.MAX; level++) {
            quads += (long) ArenaDemand.columns(level, focalPixels, subdivisionPixels, farRenderCells)
                    * ArenaDemand.cellsPerColumn(level) * budgetedQuadsPerCell(level);
        }

        long bytes = Math.ceilDiv(quads * QUAD_BYTES * WHOLE_PERCENT * WHOLE_PERCENT,
                (long) ArenaPressure.HIGH_WATER_PERCENT * BLOCK_FILL_PERCENT);
        return Math.max(bytes, MIN_BYTES);
    }

    public static long ceiling(OptionalLong texelBytes, long maxMemoryAllocationSize, OptionalLong freeBytes) {
        long ceiling = Math.min(texelBytes.orElse(UNREAD_TEXEL_BYTES), VERTEX_INDEX_BYTES);
        ceiling = Math.min(ceiling, maxMemoryAllocationSize / DEVICE_SHARE);
        return freeBytes.isPresent() ? Math.min(ceiling, freeBytes.getAsLong() / FREE_MEMORY_SHARE) : ceiling;
    }

    public static long fitted(long wanted, long ceiling) {
        long allowed = Math.min(wanted, ceiling);
        long aligned = allowed - Math.floorMod(allowed, BLOCK_BYTES);
        return aligned < MIN_BYTES ? REFUSED : aligned;
    }

    public static int blocks(long bytes) {
        return (int) (bytes / BLOCK_BYTES);
    }
}
