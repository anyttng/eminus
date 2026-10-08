package com.eminus.render.tree;

import java.util.ArrayList;
import java.util.List;

import com.eminus.mesh.CellMesh;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

import org.jspecify.annotations.Nullable;

// The render list travels with the meshes it draws, so the render thread never lists a mesh it has not uploaded.
public final class TreeBatch {
    private final Long2ObjectLinkedOpenHashMap<CellMesh> meshes = new Long2ObjectLinkedOpenHashMap<>();
    private final LongLinkedOpenHashSet evicted = new LongLinkedOpenHashSet();

    private @Nullable RenderList renderList;
    private @Nullable List<CellMesh> uploadOrder;

    // Read once the batch is published, when the tree no longer adds to it.
    public List<CellMesh> meshes() {
        if (uploadOrder == null) {
            uploadOrder = new ArrayList<>(meshes.values());
        }

        return uploadOrder;
    }

    public LongCollection evicted() {
        return evicted;
    }

    public @Nullable RenderList renderList() {
        return renderList;
    }

    void renderList(RenderList walked) {
        renderList = walked;
    }

    void add(CellMesh mesh) {
        evicted.remove(mesh.key());
        meshes.put(mesh.key(), mesh);
    }

    void evict(long key) {
        meshes.remove(key);
        evicted.add(key);
    }

    boolean isEmpty() {
        return meshes.isEmpty() && evicted.isEmpty() && renderList == null;
    }
}
