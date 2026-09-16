package com.eminus.cell;

import java.util.function.LongConsumer;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public final class ColumnCoverage {
    public static final int GRID_SIDE = DetailLevel.VOXELS_PER_SIDE + 2;

    private static final int CHUNK_SHIFT = 4;
    private static final int OUTSIDE = 1;
    private static final int Z_BITS = 32;
    private static final long Z_MASK = 0xFFFF_FFFFL;

    private final LongOpenHashSet covered = new LongOpenHashSet();
    private final LongConsumer persistence;
    private final boolean everything;

    public ColumnCoverage(LongConsumer persistence) {
        this(persistence, false);
    }

    private ColumnCoverage(LongConsumer persistence, boolean everything) {
        this.persistence = persistence;
        this.everything = everything;
    }

    public static ColumnCoverage everything() {
        return new ColumnCoverage(chunk -> { }, true);
    }

    public static long pack(int chunkX, int chunkZ) {
        return (long) chunkX << Z_BITS | chunkZ & Z_MASK;
    }

    public synchronized void load(long chunk) {
        covered.add(chunk);
    }

    public synchronized boolean cover(int chunkX, int chunkZ) {
        return covered.add(pack(chunkX, chunkZ));
    }

    public synchronized boolean covers(int chunkX, int chunkZ) {
        return everything || covered.contains(pack(chunkX, chunkZ));
    }

    public void persist(int chunkX, int chunkZ) {
        persistence.accept(pack(chunkX, chunkZ));
    }

    public synchronized void fill(long key, boolean[] grid) {
        int level = CellKey.level(key);
        int originX = CellKey.x(key) * DetailLevel.blocksPerCell(level);
        int originZ = CellKey.z(key) * DetailLevel.blocksPerCell(level);

        for (int x = -OUTSIDE; x <= DetailLevel.VOXELS_PER_SIDE; x++) {
            int chunkX = (originX + (x << level)) >> CHUNK_SHIFT;

            for (int z = -OUTSIDE; z <= DetailLevel.VOXELS_PER_SIDE; z++) {
                int chunkZ = (originZ + (z << level)) >> CHUNK_SHIFT;
                grid[index(x, z)] = everything || covered.contains(pack(chunkX, chunkZ));
            }
        }
    }

    public static int index(int voxelX, int voxelZ) {
        return (voxelX + OUTSIDE) * GRID_SIDE + voxelZ + OUTSIDE;
    }
}
