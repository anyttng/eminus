package com.eminus.cell;

import net.minecraft.core.Direction;

public final class FaceMask {
    public static final int NONE = 0;
    public static final int DOWN = 1;
    public static final int UP = 1 << 1;
    public static final int NORTH = 1 << 2;
    public static final int SOUTH = 1 << 3;
    public static final int WEST = 1 << 4;
    public static final int EAST = 1 << 5;
    public static final int ALL = DOWN | UP | NORTH | SOUTH | WEST | EAST;

    private static final int LAST_VOXEL = DetailLevel.VOXELS_PER_SIDE - 1;

    public static int bit(Direction face) {
        return 1 << face.ordinal();
    }

    public static int of(int voxelX, int voxelY, int voxelZ) {
        int mask = NONE;

        if (voxelX == 0) {
            mask |= WEST;
        } else if (voxelX == LAST_VOXEL) {
            mask |= EAST;
        }

        if (voxelY == 0) {
            mask |= DOWN;
        } else if (voxelY == LAST_VOXEL) {
            mask |= UP;
        }

        if (voxelZ == 0) {
            mask |= NORTH;
        } else if (voxelZ == LAST_VOXEL) {
            mask |= SOUTH;
        }

        return mask;
    }

    private FaceMask() {
    }
}
