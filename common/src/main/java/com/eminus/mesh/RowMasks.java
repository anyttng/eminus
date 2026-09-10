package com.eminus.mesh;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.StateTable;
import com.eminus.cell.VoxelEntry;

import net.minecraft.core.Direction;

public final class RowMasks {
    public static final int ROWS = DetailLevel.VOXELS_PER_SIDE * DetailLevel.VOXELS_PER_SIDE;

    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int FIRST_VOXEL_BIT = 1;

    private final long[] opaque = new long[ROWS];
    private final long[] nonAir = new long[ROWS];
    private final long[] positive = new long[ROWS];
    private final long[] negative = new long[ROWS];

    public void build(CellVoxels voxels, StateOpacity opacity, Direction.Axis axis) {
        for (int row = 0; row < ROWS; row++) {
            int v = row / SIDE;
            int u = row % SIDE;
            long solidBits = 0L;
            long opaqueBits = 0L;

            for (int at = -1; at <= SIDE; at++) {
                long entry = entryAt(voxels, axis, u, v, at);
                if (VoxelEntry.isAir(entry)) {
                    continue;
                }

                long bit = 1L << (at + FIRST_VOXEL_BIT);
                solidBits |= bit;
                if (opacity.opacity(VoxelEntry.state(entry)) >= StateTable.FULL_OPACITY) {
                    opaqueBits |= bit;
                }
            }

            nonAir[row] = solidBits;
            opaque[row] = opaqueBits;
            positive[row] = opaqueBits & ~(opaqueBits >>> 1);
            negative[row] = opaqueBits & ~(opaqueBits << 1);
        }
    }

    public boolean solid(int row, int voxel) {
        return isSet(nonAir[row], voxel);
    }

    public boolean opaque(int row, int voxel) {
        return isSet(opaque[row], voxel);
    }

    public boolean facesPositive(int row, int voxel) {
        return isSet(positive[row], voxel);
    }

    public boolean facesNegative(int row, int voxel) {
        return isSet(negative[row], voxel);
    }

    static long entryAt(CellVoxels voxels, Direction.Axis axis, int u, int v, int at) {
        return switch (axis) {
            case X -> voxels.entry(at, v, u);
            case Y -> voxels.entry(u, at, v);
            case Z -> voxels.entry(u, v, at);
        };
    }

    private static boolean isSet(long mask, int voxel) {
        return (mask & (1L << (voxel + FIRST_VOXEL_BIT))) != 0L;
    }
}
