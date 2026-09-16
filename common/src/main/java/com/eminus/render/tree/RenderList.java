package com.eminus.render.tree;

import java.util.List;

import com.eminus.mesh.CellMesh;

public record RenderList(List<CellMesh> meshes) {
    public static final RenderList EMPTY = new RenderList(List.of());
}
