package com.eminus.client.render.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.mesh.BakeryModels;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshService;
import com.eminus.model.ModelIndex;
import com.eminus.client.model.ClientBakery;
import com.eminus.render.arena.ArenaSizing;
import com.eminus.render.backend.BackendSupport;
import com.eminus.client.render.backend.BackendCheck;
import com.eminus.client.render.far.FarProjection;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;
import com.eminus.client.session.ClientSession;
import com.eminus.settings.Settings;
import com.eminus.settings.SettingsService;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

public final class ArenaDump {
    private static final String PROBE = "[eminus-arena]";
    private static final int RING = 1;
    private static final int CELLS = (2 * RING + 1) * (2 * RING + 1);
    private static final int TIMEOUT_SECONDS = 120;
    private static final int BYTES_PER_MIB = 1024 * 1024;
    private static final String NO_SESSION = "No dimension runtime is open.";
    private static final String NO_MESHES = "No cell around the player meshed within " + TIMEOUT_SECONDS + " seconds.";

    public static Component at(int level) {
        RenderSystem.assertOnRenderThread();
        Minecraft client = Minecraft.getInstance();
        DimensionRuntime runtime = ClientSession.runtime();
        EminusInstance instance = ClientSession.instance();

        if (runtime == null || instance == null || client.player == null) {
            Eminus.LOGGER.info("{} run level={} skipped=no-session", PROBE, level);
            return Component.literal(NO_SESSION);
        }

        Eminus.LOGGER.info("{} run level={} cells={}", PROBE, level, CELLS);

        long bytes = size(client, runtime);
        BackendSupport support = BackendCheck.run(bytes);
        GeometryArena arena = GeometryArena.create(support, bytes);
        if (arena == null) {
            return Component.literal("The renderer is disabled: " + support.reason());
        }

        try {
            return fill(arena, client, runtime, instance, level, bytes);
        } finally {
            arena.close();
        }
    }

    private static long size(Minecraft client, DimensionRuntime runtime) {
        Settings settings = SettingsService.get().settings();
        float focalPixels = FarProjection.focalPixels(client.options.fov().get(),
                client.gameRenderer.mainRenderTarget().height);
        long wanted = ArenaSizing.wanted(settings.farRenderCells(), settings.detailDistance().pixels(), focalPixels,
                runtime.lowestStoredLevel());
        long limit = RenderSystem.getDevice().getDeviceInfo().limits().maxMemoryAllocationSize();
        return ArenaSizing.fitted(wanted, limit);
    }

    private static Component fill(GeometryArena arena, Minecraft client, DimensionRuntime runtime,
            EminusInstance instance, int level, long bytes) {
        ClientBakery baking = ClientBakery.start(client);
        List<CellMesh> meshes;

        try {
            meshes = mesh(runtime, instance, baking, keys(client, runtime, level));
        } finally {
            baking.stop();
        }

        if (meshes.isEmpty()) {
            Eminus.LOGGER.error("{} stalled meshes=0", PROBE);
            return Component.literal(NO_MESHES);
        }

        accept(arena, meshes, "first");
        accept(arena, meshes, "rebuild");

        return Component.literal("Arena of " + bytes / BYTES_PER_MIB + " MiB holds " + arena.meshes()
                + " meshes in " + arena.usedBlocks() + " blocks.");
    }

    private static void accept(GeometryArena arena, List<CellMesh> meshes, String pass) {
        arena.accept(meshes);
        Eminus.LOGGER.info("{} accept pass={} meshes={} uploaded={} refused={} used-blocks={}",
                PROBE, pass, arena.meshes(), arena.uploaded(), arena.refused(), arena.usedBlocks());
    }

    private static long[] keys(Minecraft client, DimensionRuntime runtime, int level) {
        BlockPos pos = client.player.blockPosition();
        long centre = runtime.frame().keyAt(level, pos.getX(), pos.getY(), pos.getZ());
        long[] keys = new long[CELLS];
        int index = 0;

        for (int x = -RING; x <= RING; x++) {
            for (int z = -RING; z <= RING; z++) {
                keys[index++] = CellKey.pack(level, CellKey.x(centre) + x, CellKey.y(centre), CellKey.z(centre) + z);
            }
        }

        return keys;
    }

    private static List<CellMesh> mesh(DimensionRuntime runtime, EminusInstance instance,
            ClientBakery baking, long[] keys) {
        List<CellMesh> built = new ArrayList<>(keys.length);
        CountDownLatch done = new CountDownLatch(keys.length);
        MeshService service = new MeshService(
                instance.build(),
                runtime.cells(),
                runtime.coverage(),
                new BakeryModels(new ModelIndex(runtime.states(), baking.bakery()), baking.bakery()),
                baking.opacity(runtime.states()),
                (mesh, request) -> {
                    synchronized (built) {
                        built.add(mesh);
                    }

                    done.countDown();
                });

        for (long key : keys) {
            service.request(key);
        }

        return await(service, done, built);
    }

    private static List<CellMesh> await(MeshService service, CountDownLatch done, List<CellMesh> built) {
        try {
            if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Eminus.LOGGER.error("{} stalled backlog={} meshed={}", PROBE, service.backlog(), built.size());
                service.drop();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }

        synchronized (built) {
            return List.copyOf(built);
        }
    }

    private ArenaDump() {
    }
}
