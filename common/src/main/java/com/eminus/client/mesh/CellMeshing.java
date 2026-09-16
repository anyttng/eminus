package com.eminus.client.mesh;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.eminus.client.model.ClientBakery;
import com.eminus.mesh.BakeryModels;
import com.eminus.mesh.BakeryTints;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshService;
import com.eminus.model.ModelIndex;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;

import net.minecraft.client.Minecraft;

public final class CellMeshing {
    public static CompletableFuture<Map<Long, CellMesh>> mesh(DimensionRuntime runtime, EminusInstance instance,
            ClientBakery baking, long[] keys, int timeoutSeconds) {
        Map<Long, CellMesh> meshes = new ConcurrentHashMap<>();
        CompletableFuture<Map<Long, CellMesh>> done = new CompletableFuture<>();
        MeshService service = new MeshService(
                instance.build(),
                runtime.cells(),
                runtime.coverage(),
                new BakeryModels(new ModelIndex(runtime.states(), baking.bakery()), baking.bakery()),
                new BakeryTints(baking.colours(), runtime.biomes()),
                Minecraft.getInstance().options.biomeBlendRadius().get(),
                baking.opacity(runtime.states()),
                (mesh, request) -> {
                    meshes.put(mesh.key(), mesh);
                    if (meshes.size() == keys.length) {
                        done.complete(meshes);
                    }
                });

        for (long key : keys) {
            service.request(key);
        }

        return done.orTimeout(timeoutSeconds, TimeUnit.SECONDS).whenComplete((result, failure) -> {
            if (failure != null) {
                service.drop();
            }
        });
    }

    private CellMeshing() {
    }
}
