package com.eminus.mesh;

import java.util.Arrays;

import com.eminus.cell.Cell;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;

import net.minecraft.core.Direction;

public final class CellVoxels {
    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int LAST = SIDE - 1;
    private static final int LAYER_SIZE = SIDE * SIDE;
    private static final int SIDES = Direction.values().length;
    private static final int BIOME_MARGIN = TintBlend.MAX_RADIUS;
    private static final int BIOME_WINDOW = SIDE + 2 * BIOME_MARGIN;

    private static final int DOWN = Direction.DOWN.ordinal();
    private static final int UP = Direction.UP.ordinal();
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final int SOUTH = Direction.SOUTH.ordinal();
    private static final int WEST = Direction.WEST.ordinal();
    private static final int EAST = Direction.EAST.ordinal();

    private final long[] voxels = new long[DetailLevel.VOXELS_PER_CELL];
    private final long[][] layers = new long[SIDES][LAYER_SIZE];
    private final boolean[] covered = new boolean[ColumnCoverage.GRID_SIDE * ColumnCoverage.GRID_SIDE];
    private final int[] biomes = new int[BIOME_WINDOW * BIOME_WINDOW * SIDE];

    public CellVoxels() {
        Arrays.fill(covered, true);
    }

    public void load(Cell cell) {
        cell.expand(voxels);
        Arrays.fill(biomes, VoxelEntry.UNKNOWN_BIOME);

        for (int y = 0; y < SIDE; y++) {
            for (int z = 0; z < SIDE; z++) {
                for (int x = 0; x < SIDE; x++) {
                    biomes[biomeIndex(x, y, z)] = VoxelEntry.biome(voxels[DetailLevel.voxelIndex(x, y, z)]);
                }
            }
        }
    }

    public void loadBiomes(Cell neighbour, int cellX, int cellZ, int radius) {
        int offsetX = cellX * SIDE;
        int offsetZ = cellZ * SIDE;
        int fromX = Math.max(-radius, offsetX);
        int toX = Math.min(SIDE + radius, offsetX + SIDE);
        int fromZ = Math.max(-radius, offsetZ);
        int toZ = Math.min(SIDE + radius, offsetZ + SIDE);

        for (int y = 0; y < SIDE; y++) {
            for (int z = fromZ; z < toZ; z++) {
                for (int x = fromX; x < toX; x++) {
                    biomes[biomeIndex(x, y, z)] = VoxelEntry.biome(neighbour.get(x - offsetX, y, z - offsetZ));
                }
            }
        }
    }

    public int biome(int x, int y, int z) {
        return biomes[biomeIndex(x, y, z)];
    }

    public void loadCoverage(ColumnCoverage coverage, long key) {
        coverage.fill(key, covered);
    }

    public boolean covered(int x, int z) {
        return covered[ColumnCoverage.index(x, z)];
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

    private static int biomeIndex(int x, int y, int z) {
        return (y * BIOME_WINDOW + z + BIOME_MARGIN) * BIOME_WINDOW + x + BIOME_MARGIN;
    }
}
