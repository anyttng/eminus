package com.eminus.client.render.far;

import java.util.function.Supplier;

import com.eminus.cell.cache.CellHandle;
import com.eminus.mesh.MeshService;
import com.eminus.render.tree.TreeBuilds;

import org.jspecify.annotations.Nullable;

final class FarBuilds implements TreeBuilds {
    private final Supplier<MeshService> meshes;

    FarBuilds(Supplier<MeshService> meshes) {
        this.meshes = meshes;
    }

    @Override
    public void build(long key, @Nullable CellHandle handle, int references, long request, float priority) {
        meshes.get().request(key, handle, references, request, priority);
    }

    @Override
    public void release(CellHandle handle, int references) {
        meshes.get().release(handle, references);
    }

    @Override
    public int backlog() {
        return meshes.get().backlog();
    }

    @Override
    public void urgent(boolean urgent) {
        meshes.get().urgent(urgent);
    }
}
