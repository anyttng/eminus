package com.eminus.mesh;

public record MeshSummary(long key, int occupancy, int quadCount, int translucentQuads) {
    public boolean isEmpty() {
        return quadCount == 0;
    }
}
