package com.eminus.mesh;

@FunctionalInterface
public interface MeshListener {
    void meshed(CellMesh mesh, long request);

    default void failed(long key, long request) {
    }
}
