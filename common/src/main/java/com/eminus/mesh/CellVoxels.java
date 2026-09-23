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
    private static final int DIAGONALS = 4;
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
    private final long[][] edges = new long[DIAGONALS][SIDE];
    private final long[][] aboveSides = new long[SIDES][SIDE];
    private final long[] aboveCorners = new long[DIAGONALS];
    private final boolean[] covered = new boolean[ColumnCoverage.GRID_SIDE * ColumnCoverage.GRID_SIDE];
    private final int[] biomes = new int[BIOME_WINDOW * BIOME_WINDOW * SIDE];

    public CellVoxels() {
        Arrays.fill(covered, true);
    }

    public void load(Cell cell) {
        cell.expand(voxels);
        Arrays.fill(biomes, VoxelEntry.UNKNOWN_BIOME);
        for (long[] edge : edges) {
            Arrays.fill(edge, VoxelEntry.AIR);
        }

        for (long[] side : aboveSides) {
            Arrays.fill(side, VoxelEntry.AIR);
        }

        Arrays.fill(aboveCorners, VoxelEntry.AIR);

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

    public void loadDiagonal(int cellX, int cellZ, Cell neighbour) {
        long[] edge = edges[diagonal(cellX, cellZ)];
        int x = facing(cellX);
        int z = facing(cellZ);

        for (int y = 0; y < SIDE; y++) {
            edge[y] = neighbour.get(x, y, z);
        }
    }

    public void loadAboveSide(Direction side, Cell neighbour) {
        long[] row = aboveSides[side.ordinal()];

        for (int along = 0; along < SIDE; along++) {
            row[along] = side.getAxis() == Direction.Axis.X
                    ? neighbour.get(facing(side.getStepX()), 0, along)
                    : neighbour.get(along, 0, facing(side.getStepZ()));
        }
    }

    public void loadAboveCorner(int cellX, int cellZ, Cell neighbour) {
        aboveCorners[diagonal(cellX, cellZ)] = neighbour.get(facing(cellX), 0, facing(cellZ));
    }

    public long around(int x, int y, int z) {
        boolean outX = x < 0 || x > LAST;
        boolean outZ = z < 0 || z > LAST;

        if (y > LAST && (outX || outZ)) {
            if (outX && outZ) {
                return aboveCorners[diagonal(x, z)];
            }

            return outX ? aboveSides[x < 0 ? WEST : EAST][z] : aboveSides[z < 0 ? NORTH : SOUTH][x];
        }

        return outX && outZ ? edges[diagonal(x, z)][y] : entry(x, y, z);
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

    private static int diagonal(int x, int z) {
        return (x > 0 ? 1 : 0) + (z > 0 ? 2 : 0);
    }

    private static int facing(int step) {
        return step > 0 ? 0 : LAST;
    }

    private static int biomeIndex(int x, int y, int z) {
        return (y * BIOME_WINDOW + z + BIOME_MARGIN) * BIOME_WINDOW + x + BIOME_MARGIN;
    }
}
