package com.eminus.cell;

public final class OccupancyMask {
    public static final int OCTANTS = 8;
    public static final int EMPTY = 0;

    private static final int HALF_SIDE = DetailLevel.VOXELS_PER_SIDE / 2;

    public static int octantOf(int x, int y, int z) {
        return ((y / HALF_SIDE) << 2) | ((z / HALF_SIDE) << 1) | (x / HALF_SIDE);
    }

    public static int set(int mask, int octant) {
        return mask | (1 << octant);
    }

    public static int clear(int mask, int octant) {
        return mask & ~(1 << octant);
    }

    public static boolean isSet(int mask, int octant) {
        return (mask & (1 << octant)) != 0;
    }

    public static boolean isEmpty(int mask) {
        return mask == EMPTY;
    }

    private OccupancyMask() {
    }
}
