package com.eminus.mesh;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.StateLayers;
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

    public void build(CellVoxels voxels, StateOpacity opacity, MeshModels models, int voxelBlocks,
            Direction.Axis axis) {
        for (int row = 0; row < ROWS; row++) {
            int v = row / SIDE;
            int u = row % SIDE;
            long occupiedBits = 0L;
            long opaqueBits = 0L;
            long coverBits = 0L;

            for (int at = -1; at <= SIDE; at++) {
                long entry = entryAt(voxels, axis, u, v, at);
                if (VoxelEntry.isAir(entry)) {
                    continue;
                }

                long bit = 1L << (at + FIRST_VOXEL_BIT);
                occupiedBits |= bit;
                int state = VoxelEntry.state(entry);
                if (VoxelEntry.gaps(entry) != VoxelEntry.NO_GAPS
                        || models.layerBlocks(state) != StateLayers.NOT_LAYERED) {
                    continue;
                }

                if (opacity.opacity(state) >= StateTable.FULL_OPACITY) {
                    opaqueBits |= bit;
                } else if (opacity.coversGround(entry, voxelBlocks)) {
                    opaqueBits |= bit;
                    if (axis != Direction.Axis.Y) {
                        coverBits |= bit;
                    }
                }
            }

            long wholeBits = opaqueBits & ~coverBits;
            nonAir[row] = occupiedBits;
            opaque[row] = opaqueBits;
            positive[row] = (wholeBits & ~(wholeBits >>> 1)) | (coverBits & ~(opaqueBits >>> 1));
            negative[row] = (wholeBits & ~(wholeBits << 1)) | (coverBits & ~(opaqueBits << 1));
        }
    }

    public boolean occupied(int row, int voxel) {
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

    private static long entryAt(CellVoxels voxels, Direction.Axis axis, int u, int v, int at) {
        return voxels.entry(PlaneAxes.x(axis, u, v, at), PlaneAxes.y(axis, u, v, at), PlaneAxes.z(axis, u, v, at));
    }

    private static boolean isSet(long mask, int voxel) {
        return (mask & (1L << (voxel + FIRST_VOXEL_BIT))) != 0L;
    }
}
