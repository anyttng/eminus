package com.eminus.mesh;

public record CellMesh(long key, long[] quads, int[] groupStart, int[] groupCount) {
    public static CellMesh empty(long key) {
        return new CellMesh(key, new long[0], new int[QuadGroups.COUNT], new int[QuadGroups.COUNT]);
    }

    public int quadCount() {
        return quads.length;
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
}
