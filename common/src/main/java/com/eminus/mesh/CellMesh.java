package com.eminus.mesh;

import com.eminus.cell.OccupancyMask;

public record CellMesh(long key, int occupancy, long[] quads, int[] groupStart, int[] groupCount, long[] colours) {
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
        return new MeshSummary(key, occupancy, quads.length, groupCount[QuadGroups.TRANSLUCENT]);
    }

    public int slotCount() {
        return quads.length + colours.length;
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

    public long quad(int index) {
        return quads[index];
    }

    public int colour(long quad) {
        return MeshBuffer.colourOf(colours[Quad.colourIndex(quad)]);
    }

    public int offset(long quad) {
        return MeshBuffer.offsetOf(colours[Quad.colourIndex(quad)]);
    }
}
