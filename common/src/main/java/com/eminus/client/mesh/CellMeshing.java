package com.eminus.client.mesh;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.client.model.ClientBakery;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshService;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import net.minecraft.client.Minecraft;

public final class CellMeshing {
    public static CompletableFuture<Map<Long, CellMesh>> mesh(DimensionRuntime runtime, EminusInstance instance,
            ClientBakery baking, long[] keys, int timeoutSeconds) {
        Map<Long, CellMesh> meshes = new ConcurrentHashMap<>();
        CompletableFuture<Map<Long, CellMesh>> done = new CompletableFuture<>();
        MeshService service = MeshWiring.service(Minecraft.getInstance(), runtime, instance, baking,
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

    public static long[] keys(CellFrame frame, int fromLevel, int toLevel, int minX, int minY, int minZ, int maxX,
            int maxY, int maxZ) {
        LongArrayList keys = new LongArrayList();
        for (int level = fromLevel; level <= toLevel; level++) {
            for (int cellX = CellFrame.cellX(minX, level); cellX <= CellFrame.cellX(maxX, level); cellX++) {
                for (int cellY = frame.cellY(minY, level); cellY <= frame.cellY(maxY, level); cellY++) {
                    for (int cellZ = CellFrame.cellZ(minZ, level); cellZ <= CellFrame.cellZ(maxZ, level); cellZ++) {
                        keys.add(CellKey.pack(level, cellX, cellY, cellZ));
                    }
                }
            }
        }

        return keys.toLongArray();
    }

    private CellMeshing() {
    }
}
