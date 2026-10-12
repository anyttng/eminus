package com.eminus.mesh;

public record MeshSummary(long key, int occupancy, int quadCount, int translucentQuads, boolean missingColumns) {
    public boolean isEmpty() {
        return quadCount == 0;
    }
}
