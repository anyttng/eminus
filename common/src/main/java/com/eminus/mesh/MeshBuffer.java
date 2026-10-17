package com.eminus.mesh;

import java.util.Arrays;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

public final class MeshBuffer {
    public static final int MAX_QUADS_PER_GROUP = DetailLevel.VOXELS_PER_CELL;
    public static final int MAX_QUADS_PER_BORDER = DetailLevel.VOXELS_PER_SIDE * DetailLevel.VOXELS_PER_SIDE;
    public static final int MAX_QUADS = QuadGroups.FIRST_BORDER * MAX_QUADS_PER_GROUP
            + QuadGroups.DIRECTIONAL_COUNT * MAX_QUADS_PER_BORDER;
    public static final int MAX_COLOURS = Quad.MAX_COLOUR_INDEX + 1;
    public static final int UNTINTED = 0;
    public static final int WHITE = 0xFF_FFFF;
    public static final int CHANNEL_BITS = 8;
    public static final int RED_SHIFT = 2 * CHANNEL_BITS;
    public static final int GREEN_SHIFT = CHANNEL_BITS;
    public static final int BLUE_SHIFT = 0;

    private static final int ABSENT = -1;
    private static final int CHANNEL_MASK = (1 << CHANNEL_BITS) - 1;
    private static final int PLACEMENT_SHIFT = Integer.SIZE;
    private static final long COLOUR_BITS = 0xFFFF_FFFFL;
    private static final int ORDER_SHIFT = Integer.SIZE;
    private static final long INDEX_MASK = 0xFFFF_FFFFL;

    private final LongArrayList[] groups = new LongArrayList[QuadGroups.COUNT];
    private final IntArrayList[] occlusions = new IntArrayList[QuadGroups.COUNT];
    private final BooleanArrayList[] solids = new BooleanArrayList[QuadGroups.COUNT];
    private final IntArrayList translucentOrders = new IntArrayList();
    private final LongArrayList colours = new LongArrayList();
    private final Long2IntMap colourIndices = new Long2IntOpenHashMap();
    private final Int2IntLinkedOpenHashMap requested = new Int2IntLinkedOpenHashMap();

    private int truncated;
    private int unaddressable;
    private int substituted;
    private int lost;

    public MeshBuffer() {
        for (int group = 0; group < QuadGroups.COUNT; group++) {
            groups[group] = new LongArrayList();
            occlusions[group] = new IntArrayList();
            solids[group] = new BooleanArrayList();
        }

        colourIndices.defaultReturnValue(ABSENT);
        addColour(entry(WHITE, QuadPlacement.NONE));
    }

    public static int colourOf(long entry) {
        return (int) (entry & COLOUR_BITS);
    }

    public static int placementOf(long entry) {
        return (int) (entry >>> PLACEMENT_SHIFT);
    }

    public int placementAt(int colourIndex) {
        return placementOf(colours.getLong(colourIndex));
    }

    public int colourIndex(int colour, int placement) {
        long entry = entry(colour, placement);
        int index = colourIndices.get(entry);
        if (index != ABSENT) {
            return index;
        }

        requested.putIfAbsent(placement, colour);
        if (colours.size() < MAX_COLOURS) {
            return addColour(entry);
        }

        substituted++;
        int kept = nearest(colour, placement);
        if (kept != ABSENT) {
            return kept;
        }

        lost++;
        return nearest(colour, QuadPlacement.NONE);
    }

    public boolean lostPlacements() {
        return lost > 0;
    }

    public void add(int group, long quad, int occlusion) {
        add(group, quad, occlusion, false);
    }

    public void add(int group, long quad, int occlusion, boolean solid) {
        if (group == QuadGroups.TRANSLUCENT) {
            addTranslucent(quad, TranslucentOrder.unplaced(quad), occlusion);
            return;
        }

        LongArrayList quads = groups[group];
        if (quads.size() == (QuadGroups.isBorder(group) ? MAX_QUADS_PER_BORDER : MAX_QUADS_PER_GROUP)) {
            truncated++;
            return;
        }

        quads.add(quad);
        occlusions[group].add(occlusion);
        solids[group].add(solid);
    }

    public void addTranslucent(long quad, int order, int occlusion) {
        LongArrayList quads = groups[QuadGroups.TRANSLUCENT];
        if (quads.size() == MAX_QUADS_PER_GROUP) {
            truncated++;
            return;
        }

        quads.add(quad);
        occlusions[QuadGroups.TRANSLUCENT].add(occlusion);
        translucentOrders.add(order);
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
        int[] words = new int[total];
        int[] starts = new int[QuadGroups.COUNT];
        int[] counts = new int[QuadGroups.COUNT];
        int[] solidCounts = new int[QuadGroups.COUNT];
        int at = 0;

        for (int group = 0; group < QuadGroups.COUNT; group++) {
            LongArrayList quads = groups[group];
            starts[group] = at;
            counts[group] = quads.size();
            if (group == QuadGroups.TRANSLUCENT) {
                orderTranslucent(quads, occlusions[group], packed, words, at);
            } else {
                solidCounts[group] = solidFirst(group, packed, words, at);
            }
            at += quads.size();
        }

        return new CellMesh(key, occupancy, packed, OcclusionWord.pack(words, total), starts, counts, solidCounts,
                colours.toLongArray());
    }

    public void reset() {
        requested.clear();
        clear();
    }

    public void resetReserving() {
        clear();
        for (Int2IntMap.Entry placement : requested.int2IntEntrySet()) {
            long entry = entry(placement.getIntValue(), placement.getIntKey());
            if (colours.size() < MAX_COLOURS && colourIndices.get(entry) == ABSENT) {
                addColour(entry);
            }
        }
    }

    private int solidFirst(int group, long[] packed, int[] words, int at) {
        LongArrayList quads = groups[group];
        IntArrayList occlusion = occlusions[group];
        BooleanArrayList solid = solids[group];
        int next = at;

        for (int index = 0; index < quads.size(); index++) {
            if (solid.getBoolean(index)) {
                packed[next] = quads.getLong(index);
                words[next++] = occlusion.getInt(index);
            }
        }

        int solidCount = next - at;
        for (int index = 0; index < quads.size(); index++) {
            if (!solid.getBoolean(index)) {
                packed[next] = quads.getLong(index);
                words[next++] = occlusion.getInt(index);
            }
        }

        return solidCount;
    }

    private void orderTranslucent(LongArrayList quads, IntArrayList occlusion, long[] packed, int[] words, int at) {
        long[] keys = new long[quads.size()];
        for (int index = 0; index < keys.length; index++) {
            keys[index] = (long) translucentOrders.getInt(index) << ORDER_SHIFT | index;
        }

        Arrays.sort(keys);
        for (int index = 0; index < keys.length; index++) {
            int from = (int) (keys[index] & INDEX_MASK);
            packed[at + index] = quads.getLong(from);
            words[at + index] = occlusion.getInt(from);
        }
    }

    private void clear() {
        for (int group = 0; group < QuadGroups.COUNT; group++) {
            groups[group].clear();
            occlusions[group].clear();
            solids[group].clear();
        }

        translucentOrders.clear();

        colours.clear();
        colourIndices.clear();
        addColour(entry(WHITE, QuadPlacement.NONE));

        truncated = 0;
        unaddressable = 0;
        substituted = 0;
        lost = 0;
    }

    private static long entry(int colour, int placement) {
        return (long) placement << PLACEMENT_SHIFT | (colour & COLOUR_BITS);
    }

    private int addColour(long entry) {
        int index = colours.size();
        colours.add(entry);
        colourIndices.put(entry, index);
        return index;
    }

    private int nearest(int colour, int placement) {
        int best = ABSENT;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < colours.size(); index++) {
            long candidate = colours.getLong(index);
            if (placementOf(candidate) == placement) {
                int distance = distance(colour, colourOf(candidate));
                if (distance < bestDistance) {
                    best = index;
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    private static int distance(int first, int second) {
        int red = ((first >> RED_SHIFT) & CHANNEL_MASK) - ((second >> RED_SHIFT) & CHANNEL_MASK);
        int green = ((first >> GREEN_SHIFT) & CHANNEL_MASK) - ((second >> GREEN_SHIFT) & CHANNEL_MASK);
        int blue = ((first >> BLUE_SHIFT) & CHANNEL_MASK) - ((second >> BLUE_SHIFT) & CHANNEL_MASK);
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

        if (lost > 0) {
            Eminus.LOGGER.warn(
                    "The mesh of the cell at level {} ({}, {}, {}) drew {} voxels unplaced: its {} placements"
                            + " outnumber its {} colours.",
                    CellKey.level(key), CellKey.x(key), CellKey.y(key), CellKey.z(key), lost, requested.size(),
                    MAX_COLOURS);
        }
    }
}
