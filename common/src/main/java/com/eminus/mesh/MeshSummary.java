package com.eminus.mesh;

import com.eminus.cell.OccupancyMask;

public record MeshSummary(long key, int occupancy, int quadCount, int translucentQuads, boolean missingColumns,
        long grandchildren) {
    public MeshSummary(long key, int occupancy, int quadCount, int translucentQuads, boolean missingColumns) {
        this(key, occupancy, quadCount, translucentQuads, missingColumns, OccupancyMask.NO_GRANDCHILDREN);
    }

    public boolean isEmpty() {
        return quadCount == 0;
    }
}
