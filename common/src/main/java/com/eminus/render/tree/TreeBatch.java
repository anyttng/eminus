package com.eminus.render.tree;

import java.util.ArrayList;
import java.util.List;

import com.eminus.mesh.CellMesh;

public final class TreeBatch {
    private final List<CellMesh> meshes = new ArrayList<>();

    public List<CellMesh> meshes() {
        return meshes;
    }

    void add(CellMesh mesh) {
        meshes.add(mesh);
    }

    boolean isEmpty() {
        return meshes.isEmpty();
    }
}
