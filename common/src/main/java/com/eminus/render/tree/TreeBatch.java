package com.eminus.render.tree;

import java.util.ArrayList;
import java.util.List;

import com.eminus.mesh.CellMesh;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import org.jspecify.annotations.Nullable;

// The render list travels with the meshes it draws, so the render thread never lists a mesh it has not uploaded.
public final class TreeBatch {
    private final List<CellMesh> meshes = new ArrayList<>();
    private final LongArrayList evicted = new LongArrayList();

    private @Nullable RenderList renderList;

    public List<CellMesh> meshes() {
        return meshes;
    }

    public LongList evicted() {
        return evicted;
    }

    public @Nullable RenderList renderList() {
        return renderList;
    }

    void renderList(RenderList walked) {
        renderList = walked;
    }

    void add(CellMesh mesh) {
        evicted.rem(mesh.key());
        meshes.add(mesh);
    }

    void evict(long key) {
        meshes.removeIf(mesh -> mesh.key() == key);
        if (!evicted.contains(key)) {
            evicted.add(key);
        }
    }

    boolean isEmpty() {
        return meshes.isEmpty() && evicted.isEmpty() && renderList == null;
    }
}
