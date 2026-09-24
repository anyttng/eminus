package com.eminus.render.tree;

import java.util.List;

import com.eminus.mesh.MeshSummary;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;

public record RenderList(List<MeshSummary> meshes, Long2IntMap borders) {
    public static final int NO_BORDER_FACES = 0;
    public static final RenderList EMPTY = new RenderList(List.of());

    public RenderList(List<MeshSummary> meshes) {
        this(meshes, Long2IntMaps.EMPTY_MAP);
    }

    public int borderFaces(long key) {
        return borders.getOrDefault(key, NO_BORDER_FACES);
    }
}
