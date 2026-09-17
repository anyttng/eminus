package com.eminus.render.tree;

import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshSummary;
import com.eminus.mesh.QuadGroups;

final class TestMeshes {
    private static final int ONE_QUAD = 1;

    static CellMesh of(long key, int occupancy) {
        return of(key, occupancy, ONE_QUAD);
    }

    static CellMesh of(long key, int occupancy, int quads) {
        int[] counts = new int[QuadGroups.COUNT];
        counts[0] = quads;
        return new CellMesh(key, occupancy, new long[quads], new int[QuadGroups.COUNT], counts, new long[0]);
    }

    static MeshSummary summary(long key, int occupancy) {
        return of(key, occupancy).summary();
    }

    private TestMeshes() {
    }
}
