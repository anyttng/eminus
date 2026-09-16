package com.eminus.render.tree;

import com.eminus.mesh.CellMesh;
import com.eminus.mesh.QuadGroups;

final class TestMeshes {
    private static final int ONE_QUAD = 1;

    static CellMesh of(long key, int occupancy) {
        int[] counts = new int[QuadGroups.COUNT];
        counts[0] = ONE_QUAD;
        return new CellMesh(key, occupancy, new long[ONE_QUAD], new int[QuadGroups.COUNT], counts);
    }

    private TestMeshes() {
    }
}
