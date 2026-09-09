package com.eminus.cell;

import java.util.Arrays;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

public final class Cell {
    private static final int VOXELS = DetailLevel.VOXELS_PER_CELL;
    private static final int COORDINATE_MASK = DetailLevel.VOXELS_PER_SIDE - 1;
    private static final int INDEX_MASK = 0xFFFF;
    private static final int MAX_PALETTE = INDEX_MASK + 1;
    private static final int ABSENT = -1;
    private static final int INITIAL_PALETTE_CAPACITY = 16;

    private final long key;
    private final short[] indices;
    private final int[] octantCounts = new int[OccupancyMask.OCTANTS];

    private long[] palette;
    private int paletteSize;
    private int occupancy = OccupancyMask.EMPTY;
    private Long2IntMap paletteLookup;

    public static Cell blank(long key) {
        long[] palette = new long[INITIAL_PALETTE_CAPACITY];
        palette[0] = VoxelEntry.AIR;
        return new Cell(key, palette, 1, new short[VOXELS]);
    }

    public static Cell of(long key, long[] palette, short[] indices) {
        Cell cell = new Cell(key, palette, palette.length, indices);
        cell.recountOctants();
        return cell;
    }

    private Cell(long key, long[] palette, int paletteSize, short[] indices) {
        this.key = key;
        this.palette = palette;
        this.paletteSize = paletteSize;
        this.indices = indices;
    }

    public long key() {
        return key;
    }

    public int occupancy() {
        return occupancy;
    }

    public boolean isEmpty() {
        return OccupancyMask.isEmpty(occupancy);
    }

    public int paletteSize() {
        return paletteSize;
    }

    public long get(int x, int y, int z) {
        return palette[indices[DetailLevel.voxelIndex(x, y, z)] & INDEX_MASK];
    }

    public boolean set(int x, int y, int z, long entry) {
        int voxel = DetailLevel.voxelIndex(x, y, z);
        long previous = palette[indices[voxel] & INDEX_MASK];
        if (previous == entry) {
            return false;
        }

        indices[voxel] = (short) indexOf(entry);
        updateOctant(OccupancyMask.octantOf(x, y, z), previous, entry);
        return true;
    }

    public void expand(long[] scratch) {
        for (int voxel = 0; voxel < VOXELS; voxel++) {
            scratch[voxel] = palette[indices[voxel] & INDEX_MASK];
        }
    }

    public boolean readBack(long[] scratch) {
        for (int voxel = 0; voxel < VOXELS; voxel++) {
            if (palette[indices[voxel] & INDEX_MASK] != scratch[voxel]) {
                rebuild(scratch);
                return true;
            }
        }

        return false;
    }

    private void rebuild(long[] scratch) {
        long[] rebuilt = new long[INITIAL_PALETTE_CAPACITY];
        Long2IntMap lookup = newLookup(INITIAL_PALETTE_CAPACITY);
        int size = 0;

        for (int voxel = 0; voxel < VOXELS; voxel++) {
            long entry = scratch[voxel];
            int index = lookup.get(entry);
            if (index == ABSENT) {
                if (size == rebuilt.length) {
                    rebuilt = Arrays.copyOf(rebuilt, Math.min(rebuilt.length * 2, VOXELS));
                }

                rebuilt[size] = entry;
                lookup.put(entry, size);
                index = size++;
            }

            indices[voxel] = (short) index;
        }

        palette = rebuilt;
        paletteSize = size;
        paletteLookup = lookup;
        recountOctants();
    }

    private int indexOf(long entry) {
        int existing = lookup().get(entry);
        if (existing != ABSENT) {
            return existing;
        }

        if (paletteSize == MAX_PALETTE) {
            compact();
        } else if (paletteSize == palette.length) {
            palette = Arrays.copyOf(palette, Math.min(palette.length * 2, MAX_PALETTE));
        }

        palette[paletteSize] = entry;
        lookup().put(entry, paletteSize);
        return paletteSize++;
    }

    private void compact() {
        long[] compacted = new long[palette.length];
        int[] remap = new int[paletteSize];
        Arrays.fill(remap, ABSENT);
        int size = 0;

        for (int voxel = 0; voxel < VOXELS; voxel++) {
            int index = indices[voxel] & INDEX_MASK;
            if (remap[index] == ABSENT) {
                compacted[size] = palette[index];
                remap[index] = size++;
            }

            indices[voxel] = (short) remap[index];
        }

        palette = compacted;
        paletteSize = size;
        paletteLookup = null;
    }

    private Long2IntMap lookup() {
        if (paletteLookup == null) {
            Long2IntMap lookup = newLookup(paletteSize);
            for (int index = 0; index < paletteSize; index++) {
                lookup.put(palette[index], index);
            }

            paletteLookup = lookup;
        }

        return paletteLookup;
    }

    private static Long2IntMap newLookup(int capacity) {
        Long2IntOpenHashMap lookup = new Long2IntOpenHashMap(capacity);
        lookup.defaultReturnValue(ABSENT);
        return lookup;
    }

    private void updateOctant(int octant, long previous, long entry) {
        int delta = (VoxelEntry.isAir(entry) ? 0 : 1) - (VoxelEntry.isAir(previous) ? 0 : 1);
        if (delta == 0) {
            return;
        }

        octantCounts[octant] += delta;
        occupancy = octantCounts[octant] == 0
                ? OccupancyMask.clear(occupancy, octant)
                : OccupancyMask.set(occupancy, octant);
    }

    private void recountOctants() {
        Arrays.fill(octantCounts, 0);
        occupancy = OccupancyMask.EMPTY;

        for (int voxel = 0; voxel < VOXELS; voxel++) {
            if (!VoxelEntry.isAir(palette[indices[voxel] & INDEX_MASK])) {
                octantCounts[octantOfVoxel(voxel)]++;
            }
        }

        for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
            if (octantCounts[octant] > 0) {
                occupancy = OccupancyMask.set(occupancy, octant);
            }
        }
    }

    private static int octantOfVoxel(int voxel) {
        return OccupancyMask.octantOf(
                voxel & COORDINATE_MASK,
                voxel >>> (DetailLevel.SIDE_BITS * 2),
                (voxel >>> DetailLevel.SIDE_BITS) & COORDINATE_MASK);
    }
}
