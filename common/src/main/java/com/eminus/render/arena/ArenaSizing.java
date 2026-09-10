package com.eminus.render.arena;

public final class ArenaSizing {
    public static final int QUAD_BYTES = Long.BYTES;
    public static final long BLOCK_BYTES = (long) ArenaAllocator.QUADS_PER_BLOCK * QUAD_BYTES;
    public static final long MIN_BYTES = 32L * 1024 * 1024;
    public static final long MAX_BYTES = 512L * 1024 * 1024;
    public static final long BYTES_PER_TOP_CELL = 64L * 1024;
    public static final int DEVICE_SHARE = 4;
    public static final long REFUSED = 0L;

    private ArenaSizing() {
    }

    public static long wanted(int farRenderCells) {
        long side = 2L * farRenderCells + 1L;
        return Math.clamp(side * side * BYTES_PER_TOP_CELL, MIN_BYTES, MAX_BYTES);
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
