package com.eminus.mesh;

import com.eminus.cell.Cell;
import com.eminus.cell.DetailLevel;

import net.minecraft.core.Direction;

public final class CellVoxels {
    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int LAST = SIDE - 1;
    private static final int LAYER_SIZE = SIDE * SIDE;

    private static final int DOWN = Direction.DOWN.ordinal();
    private static final int UP = Direction.UP.ordinal();
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final int SOUTH = Direction.SOUTH.ordinal();
    private static final int WEST = Direction.WEST.ordinal();
    private static final int EAST = Direction.EAST.ordinal();

    private final long[] voxels = new long[DetailLevel.VOXELS_PER_CELL];
    private final long[][] layers = new long[QuadGroups.FACE_COUNT][LAYER_SIZE];

    public void load(Cell cell) {
        cell.expand(voxels);
    }

    public void loadNeighbour(Direction face, Cell neighbour) {
        long[] layer = layers[face.ordinal()];
        int at = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : LAST;

        switch (face.getAxis()) {
            case X -> {
                for (int y = 0; y < SIDE; y++) {
                    for (int z = 0; z < SIDE; z++) {
                        layer[y * SIDE + z] = neighbour.get(at, y, z);
                    }
                }
            }
            case Y -> {
                for (int z = 0; z < SIDE; z++) {
                    for (int x = 0; x < SIDE; x++) {
                        layer[z * SIDE + x] = neighbour.get(x, at, z);
                    }
                }
            }
            case Z -> {
                for (int y = 0; y < SIDE; y++) {
                    for (int x = 0; x < SIDE; x++) {
                        layer[y * SIDE + x] = neighbour.get(x, y, at);
                    }
                }
            }
        }
    }

    public long inside(int x, int y, int z) {
        return voxels[DetailLevel.voxelIndex(x, y, z)];
    }

    // One coordinate at a time leaves the cell; the passes walk a plane and step along its axis alone.
    public long entry(int x, int y, int z) {
        if (x < 0) {
            return layers[WEST][y * SIDE + z];
        }

        if (x > LAST) {
            return layers[EAST][y * SIDE + z];
        }

        if (y < 0) {
            return layers[DOWN][z * SIDE + x];
        }

        if (y > LAST) {
            return layers[UP][z * SIDE + x];
        }

        if (z < 0) {
            return layers[NORTH][y * SIDE + x];
        }

        if (z > LAST) {
            return layers[SOUTH][y * SIDE + x];
        }

        return voxels[DetailLevel.voxelIndex(x, y, z)];
    }
}
