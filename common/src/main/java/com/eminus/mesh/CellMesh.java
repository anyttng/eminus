package com.eminus.mesh;

import com.eminus.cell.OccupancyMask;

@SuppressWarnings("ArrayRecordComponent")
public record CellMesh(long key, int occupancy, long[] quads, long[] occlusion, int[] groupStart, int[] groupCount,
        int[] groupSolid, long[] colors, boolean missingColumns, long grandchildren, boolean readsBelow) {
    public CellMesh(long key, int occupancy, long[] quads, int[] groupStart, int[] groupCount, long[] colors) {
        this(key, occupancy, quads, new long[0], groupStart, groupCount, new int[QuadGroups.COUNT], colors);
    }

    public CellMesh(long key, int occupancy, long[] quads, long[] occlusion, int[] groupStart, int[] groupCount,
            int[] groupSolid, long[] colors) {
        this(key, occupancy, quads, occlusion, groupStart, groupCount, groupSolid, colors, false,
                OccupancyMask.NO_GRANDCHILDREN, false);
    }

    public static CellMesh empty(long key) {
        return empty(key, OccupancyMask.EMPTY);
    }

    public static CellMesh empty(long key, int occupancy) {
        return new CellMesh(key, occupancy, new long[0], new int[QuadGroups.COUNT], new int[QuadGroups.COUNT],
                new long[0]);
    }

    public int quadCount() {
        return quads.length;
    }

    public MeshSummary summary() {
        return new MeshSummary(key, occupancy, quads.length, groupCount[QuadGroups.TRANSLUCENT], missingColumns,
                grandchildren, readsBelow);
    }

    public CellMesh lackingColumns() {
        return new CellMesh(key, occupancy, quads, occlusion, groupStart, groupCount, groupSolid, colors, true,
                grandchildren, readsBelow);
    }

    public CellMesh withGrandchildren(long mask) {
        return new CellMesh(key, occupancy, quads, occlusion, groupStart, groupCount, groupSolid, colors,
                missingColumns, mask, readsBelow);
    }

    public CellMesh readingBelow(boolean read) {
        return new CellMesh(key, occupancy, quads, occlusion, groupStart, groupCount, groupSolid, colors,
                missingColumns, grandchildren, read);
    }

    public int slotCount() {
        return quads.length + occlusion.length + colors.length;
    }

    public boolean isEmpty() {
        return quads.length == 0;
    }

    public int groupStart(int group) {
        return groupStart[group];
    }

    public int groupCount(int group) {
        return groupCount[group];
    }

    public int groupSolid(int group) {
        return groupSolid[group];
    }

    public long quad(int index) {
        return quads[index];
    }

    public int occlusion(int index) {
        return OcclusionWord.unpack(occlusion, index);
    }

    public int color(long quad) {
        return MeshBuffer.colorOf(colors[Quad.colorIndex(quad)]);
    }

    public int placement(long quad) {
        return MeshBuffer.placementOf(colors[Quad.colorIndex(quad)]);
    }
}
