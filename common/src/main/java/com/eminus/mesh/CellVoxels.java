package com.eminus.mesh;

import java.util.Arrays;

import com.eminus.cell.Cell;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;

public final class CellVoxels {
    public static final int APRON = 2;

    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int PADDED = SIDE + 2 * APRON;
    private static final int BIOME_MARGIN = TintBlend.MAX_RADIUS;
    private static final int BIOME_WINDOW = SIDE + 2 * BIOME_MARGIN;

    private final long[] interior = new long[DetailLevel.VOXELS_PER_CELL];
    private final long[] padded = new long[PADDED * PADDED * PADDED];
    private final boolean[] covered = new boolean[ColumnCoverage.GRID_SIDE * ColumnCoverage.GRID_SIDE];
    private final int[] biomes = new int[BIOME_WINDOW * BIOME_WINDOW * SIDE];
    private final long[] below = new long[DetailLevel.VOXELS_PER_CELL];

    private boolean belowLoaded;

    public CellVoxels() {
        Arrays.fill(covered, true);
    }

    public void load(Cell cell) {
        cell.expand(interior);
        belowLoaded = false;
        Arrays.fill(biomes, VoxelEntry.UNKNOWN_BIOME);
        Arrays.fill(padded, VoxelEntry.AIR);

        for (int y = 0; y < SIDE; y++) {
            for (int z = 0; z < SIDE; z++) {
                System.arraycopy(interior, DetailLevel.voxelIndex(0, y, z), padded, index(0, y, z), SIDE);
                for (int x = 0; x < SIDE; x++) {
                    biomes[biomeIndex(x, y, z)] = VoxelEntry.biome(interior[DetailLevel.voxelIndex(x, y, z)]);
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

    public void loadRegion(int stepX, int stepY, int stepZ, Cell neighbour) {
        for (int y = from(stepY); y < to(stepY); y++) {
            for (int z = from(stepZ); z < to(stepZ); z++) {
                for (int x = from(stepX); x < to(stepX); x++) {
                    padded[index(x, y, z)] = neighbour.get(x - stepX * SIDE, y - stepY * SIDE, z - stepZ * SIDE);
                }
            }
        }
    }

    public boolean holdsNear(int stepX, int stepY, int stepZ) {
        for (int y = nearFrom(stepY); y < nearTo(stepY); y++) {
            for (int z = nearFrom(stepZ); z < nearTo(stepZ); z++) {
                for (int x = nearFrom(stepX); x < nearTo(stepX); x++) {
                    if (!VoxelEntry.isAir(padded[index(x, y, z)])) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void loadBelow(Cell neighbour) {
        neighbour.expand(below);
        belowLoaded = true;
    }

    public boolean belowLoaded() {
        return belowLoaded;
    }

    public long below(int x, int y, int z) {
        return below[DetailLevel.voxelIndex(x, y, z)];
    }

    public long entry(int x, int y, int z) {
        return padded[index(x, y, z)];
    }

    private static int from(int step) {
        return step < 0 ? -APRON : step > 0 ? SIDE : 0;
    }

    private static int to(int step) {
        return step < 0 ? 0 : step > 0 ? SIDE + APRON : SIDE;
    }

    private static int nearFrom(int step) {
        return step > 0 ? SIDE - APRON : 0;
    }

    private static int nearTo(int step) {
        return step < 0 ? APRON : SIDE;
    }

    private static int index(int x, int y, int z) {
        return ((y + APRON) * PADDED + z + APRON) * PADDED + x + APRON;
    }

    private static int biomeIndex(int x, int y, int z) {
        return (y * BIOME_WINDOW + z + BIOME_MARGIN) * BIOME_WINDOW + x + BIOME_MARGIN;
    }
}
