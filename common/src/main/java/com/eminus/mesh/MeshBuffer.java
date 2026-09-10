package com.eminus.mesh;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;

import it.unimi.dsi.fastutil.longs.LongArrayList;

public final class MeshBuffer {
    public static final int MAX_QUADS_PER_GROUP = DetailLevel.VOXELS_PER_CELL;

    private final LongArrayList[] groups = new LongArrayList[QuadGroups.COUNT];

    private int truncated;
    private int unaddressable;

    public MeshBuffer() {
        for (int group = 0; group < QuadGroups.COUNT; group++) {
            groups[group] = new LongArrayList();
        }
    }

    public void add(int group, long quad) {
        LongArrayList quads = groups[group];
        if (quads.size() == MAX_QUADS_PER_GROUP) {
            truncated++;
            return;
        }

        quads.add(quad);
    }

    public void dropUnaddressable() {
        unaddressable++;
    }

    public CellMesh freeze(long key) {
        report(key);

        int total = 0;
        for (LongArrayList quads : groups) {
            total += quads.size();
        }

        if (total == 0) {
            return CellMesh.empty(key);
        }

        long[] packed = new long[total];
        int[] starts = new int[QuadGroups.COUNT];
        int[] counts = new int[QuadGroups.COUNT];
        int at = 0;

        for (int group = 0; group < QuadGroups.COUNT; group++) {
            LongArrayList quads = groups[group];
            starts[group] = at;
            counts[group] = quads.size();
            quads.getElements(0, packed, at, quads.size());
            at += quads.size();
        }

        return new CellMesh(key, packed, starts, counts);
    }

    public void reset() {
        for (LongArrayList quads : groups) {
            quads.clear();
        }

        truncated = 0;
        unaddressable = 0;
    }

    private void report(long key) {
        if (truncated > 0) {
            Eminus.LOGGER.warn(
                    "The mesh of the cell at level {} ({}, {}, {}) dropped {} quads over the group cap of {}.",
                    CellKey.level(key), CellKey.x(key), CellKey.y(key), CellKey.z(key), truncated,
                    MAX_QUADS_PER_GROUP);
        }

        if (unaddressable > 0) {
            Eminus.LOGGER.warn(
                    "The mesh of the cell at level {} ({}, {}, {}) dropped {} voxels whose model id is past {}.",
                    CellKey.level(key), CellKey.x(key), CellKey.y(key), CellKey.z(key), unaddressable,
                    Quad.MAX_MODEL_ID);
        }
    }
}
