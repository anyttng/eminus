package com.eminus.mesh;

@FunctionalInterface
public interface MeshListener {
    void meshed(CellMesh mesh, long request);
}
