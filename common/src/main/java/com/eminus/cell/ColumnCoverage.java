package com.eminus.cell;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

public final class ColumnCoverage {
    public static final int GRID_SIDE = DetailLevel.VOXELS_PER_SIDE + 2;

    private static final int CHUNK_SHIFT = 4;
    private static final int OUTSIDE = 1;
    private static final int Z_BITS = 32;
    private static final long Z_MASK = 0xFFFF_FFFFL;
    private static final int UNCOVERED = DetailLevel.MAX + 1;

    @FunctionalInterface
    public interface Persistence {
        void persist(long chunk, int lowestLevel);
    }

    private final Long2IntOpenHashMap lowest = new Long2IntOpenHashMap();
    private final int storedLevel;
    private final Persistence persistence;
    private final boolean everything;

    public ColumnCoverage(int storedLevel, Persistence persistence) {
        this(storedLevel, persistence, false);
    }

    private ColumnCoverage(int storedLevel, Persistence persistence, boolean everything) {
        this.storedLevel = storedLevel;
        this.persistence = persistence;
        this.everything = everything;
        lowest.defaultReturnValue(UNCOVERED);
    }

    public static ColumnCoverage everything() {
        return new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { }, true);
    }

    public static long pack(int chunkX, int chunkZ) {
        return (long) chunkX << Z_BITS | (chunkZ & Z_MASK);
    }

    public synchronized void load(long chunk, int lowestLevel) {
        lowest.put(chunk, Math.min(lowest.get(chunk), lowestLevel));
    }

    public synchronized boolean cover(int chunkX, int chunkZ) {
        long chunk = pack(chunkX, chunkZ);
        if (lowest.get(chunk) <= storedLevel) {
            return false;
        }

        lowest.put(chunk, storedLevel);
        return true;
    }

    public synchronized boolean covers(int chunkX, int chunkZ) {
        return everything || lowest.get(pack(chunkX, chunkZ)) <= storedLevel;
    }

    public void persist(int chunkX, int chunkZ) {
        persistence.persist(pack(chunkX, chunkZ), storedLevel);
    }

    public synchronized void fill(long key, boolean[] grid) {
        int level = CellKey.level(key);
        for (int x = -OUTSIDE; x <= DetailLevel.VOXELS_PER_SIDE; x++) {
            int chunkX = CellFrame.blockXOf(key, x) >> CHUNK_SHIFT;

            for (int z = -OUTSIDE; z <= DetailLevel.VOXELS_PER_SIDE; z++) {
                int chunkZ = CellFrame.blockZOf(key, z) >> CHUNK_SHIFT;
                grid[index(x, z)] = everything || lowest.get(pack(chunkX, chunkZ)) <= level;
            }
        }
    }

    public synchronized boolean missingColumns(long key) {
        int level = CellKey.level(key);
        if (everything || level == DetailLevel.MAX) {
            return false;
        }

        for (int x = 0; x < DetailLevel.VOXELS_PER_SIDE; x++) {
            int chunkX = CellFrame.blockXOf(key, x) >> CHUNK_SHIFT;

            for (int z = 0; z < DetailLevel.VOXELS_PER_SIDE; z++) {
                int chunkZ = CellFrame.blockZOf(key, z) >> CHUNK_SHIFT;
                if (lowest.get(pack(chunkX, chunkZ)) == level + 1) {
                    return true;
                }
            }
        }

        return false;
    }

    public static int index(int voxelX, int voxelZ) {
        return (voxelX + OUTSIDE) * GRID_SIDE + voxelZ + OUTSIDE;
    }
}
