package com.eminus.client.mesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.eminus.cell.CellKey;
import com.eminus.client.model.ClientBakery;
import com.eminus.client.render.far.FarRenderer;
import com.eminus.client.session.ClientSession;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.Quad;
import com.eminus.model.ModelBakery;
import com.eminus.model.PackBlocks;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;

import net.minecraft.client.Minecraft;

import it.unimi.dsi.fastutil.ints.IntSet;

import org.jspecify.annotations.Nullable;

public final class CellQuadReading {
    public static final int LEVEL = 0;
    public static final int CELL_X = 1;
    public static final int CELL_Y = 2;
    public static final int CELL_Z = 3;
    public static final int OPAQUE_QUADS = 4;
    public static final int SEE_THROUGH_QUADS = 5;
    public static final int PACK_QUADS = 6;
    public static final int PACK_MERGED_QUADS = 7;
    public static final int RENDERED_CUTOUT = 8;
    public static final int SEE_THROUGH_TWINNED_QUADS = 9;

    private static final int TIMEOUT_SECONDS = 120;

    public static @Nullable CompletableFuture<List<int[]>> start(int level, int blockX, int blockY, int blockZ,
            int ring) {
        Minecraft client = Minecraft.getInstance();
        DimensionRuntime runtime = ClientSession.runtime();
        EminusInstance instance = ClientSession.instance();
        FarRenderer renderer = ClientSession.renderer();
        if (runtime == null || instance == null || renderer == null) {
            return null;
        }

        long[] keys = square(runtime.frame().keyAt(level, blockX, blockY, blockZ), ring);
        PackBlocks pack = ClientSession.packBlocks();
        boolean renderedCutout = renderer.cutoutLeaves();
        ClientBakery opaqueBaking = ClientBakery.start(client, false);
        ClientBakery seeThroughBaking = ClientBakery.start(client, true);
        ClientBakery packBaking = ClientBakery.start(client, renderedCutout, pack);
        ClientBakery packMergedBaking = ClientBakery.start(client, renderedCutout,
                new PackBlocks(pack.ids(), IntSet.of()));
        CompletableFuture<Map<Long, CellMesh>> opaque = CellMeshing.mesh(runtime, instance, opaqueBaking, keys,
                TIMEOUT_SECONDS);
        CompletableFuture<Map<Long, CellMesh>> seeThrough = CellMeshing.mesh(runtime, instance, seeThroughBaking,
                keys, TIMEOUT_SECONDS);
        CompletableFuture<Map<Long, CellMesh>> packed = CellMeshing.mesh(runtime, instance, packBaking, keys,
                TIMEOUT_SECONDS);
        CompletableFuture<Map<Long, CellMesh>> packMerged = CellMeshing.mesh(runtime, instance, packMergedBaking,
                keys, TIMEOUT_SECONDS);

        return CompletableFuture.allOf(opaque, seeThrough, packed, packMerged)
                .thenApply(meshed -> rows(keys, opaque.join(), seeThrough.join(), packed.join(), packMerged.join(),
                        renderedCutout, seeThroughBaking.bakery()))
                .whenComplete((rows, failure) -> {
                    opaqueBaking.stop();
                    seeThroughBaking.stop();
                    packBaking.stop();
                    packMergedBaking.stop();
                });
    }

    private static long[] square(long center, int ring) {
        int side = 2 * ring + 1;
        long[] keys = new long[side * side];
        int index = 0;

        for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
                keys[index++] = CellKey.pack(CellKey.level(center), CellKey.x(center) + dx, CellKey.y(center),
                        CellKey.z(center) + dz);
            }
        }

        return keys;
    }

    private static List<int[]> rows(long[] keys, Map<Long, CellMesh> opaque, Map<Long, CellMesh> seeThrough,
            Map<Long, CellMesh> packed, Map<Long, CellMesh> packMerged, boolean renderedCutout,
            ModelBakery seeThroughBakery) {
        List<int[]> rows = new ArrayList<>(keys.length);
        for (long key : keys) {
            rows.add(new int[] {CellKey.level(key), CellKey.x(key), CellKey.y(key), CellKey.z(key),
                    opaque.get(key).quadCount(), seeThrough.get(key).quadCount(), packed.get(key).quadCount(),
                    packMerged.get(key).quadCount(), renderedCutout ? 1 : 0,
                    twinnedQuads(seeThrough.get(key), seeThroughBakery)});
        }

        return rows;
    }

    private static int twinnedQuads(CellMesh mesh, ModelBakery bakery) {
        int twinned = 0;
        for (int index = 0; index < mesh.quadCount(); index++) {
            int modelId = Quad.modelId(mesh.quad(index));
            if (bakery.opaqueModelId(modelId) != modelId) {
                twinned++;
            }
        }

        return twinned;
    }

    private CellQuadReading() {
    }
}
