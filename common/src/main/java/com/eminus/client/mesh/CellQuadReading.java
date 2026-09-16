package com.eminus.client.mesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.eminus.cell.CellKey;
import com.eminus.client.model.ClientBakery;
import com.eminus.client.session.ClientSession;
import com.eminus.mesh.CellMesh;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;

import net.minecraft.client.Minecraft;

import org.jspecify.annotations.Nullable;

public final class CellQuadReading {
    public static final int LEVEL = 0;
    public static final int CELL_X = 1;
    public static final int CELL_Y = 2;
    public static final int CELL_Z = 3;
    public static final int OPAQUE_QUADS = 4;
    public static final int SEE_THROUGH_QUADS = 5;

    private static final int TIMEOUT_SECONDS = 120;

    public static @Nullable CompletableFuture<List<int[]>> start(int level, int blockX, int blockY, int blockZ,
            int ring) {
        Minecraft client = Minecraft.getInstance();
        DimensionRuntime runtime = ClientSession.runtime();
        EminusInstance instance = ClientSession.instance();
        if (runtime == null || instance == null) {
            return null;
        }

        long[] keys = square(runtime.frame().keyAt(level, blockX, blockY, blockZ), ring);
        ClientBakery opaqueBaking = ClientBakery.start(client, false);
        ClientBakery seeThroughBaking = ClientBakery.start(client, true);

        return CellMeshing.mesh(runtime, instance, opaqueBaking, keys, TIMEOUT_SECONDS)
                .thenCombine(CellMeshing.mesh(runtime, instance, seeThroughBaking, keys, TIMEOUT_SECONDS),
                        (opaque, seeThrough) -> rows(keys, opaque, seeThrough))
                .whenComplete((rows, failure) -> {
                    opaqueBaking.stop();
                    seeThroughBaking.stop();
                });
    }

    private static long[] square(long centre, int ring) {
        int side = 2 * ring + 1;
        long[] keys = new long[side * side];
        int index = 0;

        for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
                keys[index++] = CellKey.pack(CellKey.level(centre), CellKey.x(centre) + dx, CellKey.y(centre),
                        CellKey.z(centre) + dz);
            }
        }

        return keys;
    }

    private static List<int[]> rows(long[] keys, Map<Long, CellMesh> opaque, Map<Long, CellMesh> seeThrough) {
        List<int[]> rows = new ArrayList<>(keys.length);
        for (long key : keys) {
            rows.add(new int[] {CellKey.level(key), CellKey.x(key), CellKey.y(key), CellKey.z(key),
                    opaque.get(key).quadCount(), seeThrough.get(key).quadCount()});
        }

        return rows;
    }

    private CellQuadReading() {
    }
}
