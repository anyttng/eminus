package com.eminus.render.tree;

import java.util.List;

import com.eminus.mesh.MeshSummary;

public record RenderList(List<MeshSummary> meshes) {
    public static final RenderList EMPTY = new RenderList(List.of());
}
