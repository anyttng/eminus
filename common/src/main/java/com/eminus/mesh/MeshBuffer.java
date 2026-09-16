package com.eminus.mesh;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

public final class MeshBuffer {
    public static final int MAX_QUADS_PER_GROUP = DetailLevel.VOXELS_PER_CELL;
    public static final int MAX_COLOURS = Quad.MAX_COLOUR_INDEX + 1;
    public static final int UNTINTED = 0;
    public static final int WHITE = 0xFF_FFFF;

    private static final int ABSENT = -1;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL_MASK = 0xFF;

    private final LongArrayList[] groups = new LongArrayList[QuadGroups.COUNT];
    private final IntArrayList colours = new IntArrayList();
    private final Int2IntMap colourIndices = new Int2IntOpenHashMap();

    private int truncated;
    private int unaddressable;
    private int substituted;

    public MeshBuffer() {
        for (int group = 0; group < QuadGroups.COUNT; group++) {
            groups[group] = new LongArrayList();
        }

        colourIndices.defaultReturnValue(ABSENT);
        addColour(WHITE);
    }

    public int colourIndex(int colour) {
        int index = colourIndices.get(colour);
        if (index != ABSENT) {
            return index;
        }

        if (colours.size() < MAX_COLOURS) {
            return addColour(colour);
        }

        substituted++;
        return nearest(colour);
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

    public CellMesh freeze(long key, int occupancy) {
        report(key);

        int total = 0;
        for (LongArrayList quads : groups) {
            total += quads.size();
        }

        if (total == 0) {
            return CellMesh.empty(key, occupancy);
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

        return new CellMesh(key, occupancy, packed, starts, counts, colours.toIntArray());
    }

    public void reset() {
        for (LongArrayList quads : groups) {
            quads.clear();
        }

        colours.clear();
        colourIndices.clear();
        addColour(WHITE);

        truncated = 0;
        unaddressable = 0;
        substituted = 0;
    }

    private int addColour(int colour) {
        int index = colours.size();
        colours.add(colour);
        colourIndices.put(colour, index);
        return index;
    }

    private int nearest(int colour) {
        int best = UNTINTED;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < colours.size(); index++) {
            int distance = distance(colour, colours.getInt(index));
            if (distance < bestDistance) {
                best = index;
                bestDistance = distance;
            }
        }

        return best;
    }

    private static int distance(int first, int second) {
        int red = ((first >> RED_SHIFT) & CHANNEL_MASK) - ((second >> RED_SHIFT) & CHANNEL_MASK);
        int green = ((first >> GREEN_SHIFT) & CHANNEL_MASK) - ((second >> GREEN_SHIFT) & CHANNEL_MASK);
        int blue = (first & CHANNEL_MASK) - (second & CHANNEL_MASK);
        return red * red + green * green + blue * blue;
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

        if (substituted > 0) {
            Eminus.LOGGER.warn(
                    "The mesh of the cell at level {} ({}, {}, {}) drew {} voxels in the nearest of its {} colours.",
                    CellKey.level(key), CellKey.x(key), CellKey.y(key), CellKey.z(key), substituted, MAX_COLOURS);
        }
    }
}
