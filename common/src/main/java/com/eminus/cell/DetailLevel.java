package com.eminus.cell;

public final class DetailLevel {
    public static final int MIN = 0;
    public static final int MAX = 4;
    public static final int COUNT = MAX - MIN + 1;

    public static final int SIDE_BITS = 5;
    public static final int VOXELS_PER_SIDE = 1 << SIDE_BITS;
    public static final int VOXELS_PER_CELL = VOXELS_PER_SIDE * VOXELS_PER_SIDE * VOXELS_PER_SIDE;

    public static int blocksPerVoxel(int level) {
        return 1 << level;
    }

    public static int blocksPerCell(int level) {
        return VOXELS_PER_SIDE << level;
    }

    public static int voxelIndex(int x, int y, int z) {
        return (y << (SIDE_BITS * 2)) | (z << SIDE_BITS) | x;
    }

    private DetailLevel() {
    }
}
