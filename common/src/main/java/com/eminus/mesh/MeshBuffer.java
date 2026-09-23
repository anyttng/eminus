package com.eminus.mesh;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
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
    private static final int OFFSET_SHIFT = Integer.SIZE;
    private static final long COLOUR_BITS = 0xFFFF_FFFFL;
    private static final int OFFSET_MISMATCH = 1 << 18;

    private final LongArrayList[] groups = new LongArrayList[QuadGroups.COUNT];
    private final LongArrayList colours = new LongArrayList();
    private final Long2IntMap colourIndices = new Long2IntOpenHashMap();

    private int truncated;
    private int unaddressable;
    private int substituted;

    public MeshBuffer() {
        for (int group = 0; group < QuadGroups.COUNT; group++) {
            groups[group] = new LongArrayList();
        }

        colourIndices.defaultReturnValue(ABSENT);
        addColour(entry(WHITE, QuadOffset.NONE));
    }

    public static int colourOf(long entry) {
        return (int) (entry & COLOUR_BITS);
    }

    public static int offsetOf(long entry) {
        return (int) (entry >>> OFFSET_SHIFT);
    }

    public int colourIndex(int colour, int offset) {
        long entry = entry(colour, offset);
        int index = colourIndices.get(entry);
        if (index != ABSENT) {
            return index;
        }

        if (colours.size() < MAX_COLOURS) {
            return addColour(entry);
        }

        substituted++;
        return nearest(entry);
    }

    public int cornerIndex(int colour, int corners) {
        long entry = entry(colour, corners);
        int index = colourIndices.get(entry);
        if (index != ABSENT) {
            return index;
        }

        if (colours.size() < MAX_COLOURS) {
            return addColour(entry);
        }

        substituted++;
        long flat = entry(colour, QuadOffset.NONE);
        int flatIndex = colourIndices.get(flat);
        return flatIndex != ABSENT ? flatIndex : nearest(flat);
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

        return new CellMesh(key, occupancy, packed, starts, counts, colours.toLongArray());
    }

    public void reset() {
        for (LongArrayList quads : groups) {
            quads.clear();
        }

        colours.clear();
        colourIndices.clear();
        addColour(entry(WHITE, QuadOffset.NONE));

        truncated = 0;
        unaddressable = 0;
        substituted = 0;
    }

    private static long entry(int colour, int offset) {
        return (long) offset << OFFSET_SHIFT | colour & COLOUR_BITS;
    }

    private int addColour(long entry) {
        int index = colours.size();
        colours.add(entry);
        colourIndices.put(entry, index);
        return index;
    }

    private int nearest(long entry) {
        int best = UNTINTED;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < colours.size(); index++) {
            long candidate = colours.getLong(index);
            int distance = distance(colourOf(entry), colourOf(candidate))
                    + (offsetOf(entry) == offsetOf(candidate) ? 0 : OFFSET_MISMATCH);
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
