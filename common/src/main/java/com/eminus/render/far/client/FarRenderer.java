package com.eminus.render.far.client;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.cache.CellHandle;
import com.eminus.mesh.BakeryModels;
import com.eminus.mesh.MeshService;
import com.eminus.model.ModelIndex;
import com.eminus.model.client.ClientBakery;
import com.eminus.render.arena.ArenaSizing;
import com.eminus.render.arena.MeshSlot;
import com.eminus.render.arena.client.GeometryArena;
import com.eminus.render.backend.BackendSupport;
import com.eminus.render.backend.client.BackendCheck;
import com.eminus.render.far.DrawCommands;
import com.eminus.render.tree.CameraFrame;
import com.eminus.render.tree.RenderList;
import com.eminus.render.tree.TreeBatch;
import com.eminus.render.tree.TreeBuilds;
import com.eminus.render.tree.TreeExtent;
import com.eminus.render.tree.TreeManager;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;
import com.eminus.settings.FarDistance;
import com.eminus.settings.Settings;
import com.eminus.settings.SettingsService;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

public final class FarRenderer implements AutoCloseable {
    public static final int COMMAND_CAPACITY = 32768;

    private static final String PROBE = "[eminus-tree]";

    private final DimensionRuntime runtime;
    private final ClientBakery baking;
    private final ModelPublisher models;
    private final GeometryArena arena;
    private final FarTarget target;
    private final OpaquePass opaque;
    private final CompositePass composite;
    private final IndirectCommands indirect;
    private final DrawCommands commands = new DrawCommands(COMMAND_CAPACITY);
    private final FarProjection projection = new FarProjection();
    private final LevelProjection levelProjection = new LevelProjection();
    private final Matrix4f farViewProjection = new Matrix4f();
    private final Matrix4f gameViewProjection = new Matrix4f();
    private final Matrix4f viewRotation = new Matrix4f();
    private final TreeManager tree;

    private volatile MeshService meshes;
    private RenderList renderList = RenderList.EMPTY;
    private boolean stopped;

    private FarRenderer(DimensionRuntime runtime, ClientBakery baking, ModelPublisher models, GeometryArena arena,
            FarTarget target, OpaquePass opaque, CompositePass composite, IndirectCommands indirect,
            int heightCells) {
        this.runtime = runtime;
        this.baking = baking;
        this.models = models;
        this.arena = arena;
        this.target = target;
        this.opaque = opaque;
        this.composite = composite;
        this.indirect = indirect;
        tree = TreeManager.start(new Builds(),
                new TreeExtent(runtime.frame(), heightCells, runtime.lowestStoredLevel()));
    }

    public static @Nullable FarRenderer start(Minecraft client, EminusInstance instance, DimensionRuntime runtime,
            int levelHeight) {
        RenderSystem.assertOnRenderThread();

        long bytes = ArenaSizing.fitted(ArenaSizing.wanted(SettingsService.get().settings().farRenderCells()),
                RenderSystem.getDevice().getDeviceInfo().limits().maxMemoryAllocationSize());
        BackendSupport support = BackendCheck.run(bytes);
        GeometryArena arena = GeometryArena.create(support, bytes);
        if (arena == null) {
            return null;
        }

        RenderTarget main = client.gameRenderer.mainRenderTarget();
        ClientBakery baking = ClientBakery.start(client);
        FarRenderer renderer = new FarRenderer(runtime, baking,
                ModelPublisher.start(baking.bakery(), baking.colours(), runtime.biomes()), arena,
                FarTarget.create(support.depthStencilFormat(), main.width, main.height),
                OpaquePass.create(support.depth()), CompositePass.create(support.depth()),
                IndirectCommands.create(COMMAND_CAPACITY),
                Math.ceilDiv(levelHeight, FarDistance.BLOCKS_PER_TOP_LEVEL_CELL));

        renderer.meshes = new MeshService(instance.build(), runtime.cells(),
                new BakeryModels(new ModelIndex(runtime.states(), baking.bakery()), baking.bakery()),
                runtime.states(), renderer.tree);
        runtime.listenTo(renderer.tree);
        Eminus.LOGGER.info("Far renderer started for {}", runtime.identity().dimension());

        return renderer;
    }

    public void captureLevelProjection(Matrix4fc levelProjection, Matrix4fc cameraProjection) {
        this.levelProjection.capture(levelProjection, cameraProjection);
    }

    public void frame(Minecraft client) {
        RenderSystem.assertOnRenderThread();
        if (stopped) {
            return;
        }

        models.publish();

        TreeBatch batch = tree.batches().take();
        if (batch != null) {
            arena.evict(batch.evicted());
            arena.accept(batch.meshes());
            RenderList walked = batch.renderList();
            if (walked != null) {
                renderList = walked;
            }
        }

        RenderTarget main = client.gameRenderer.mainRenderTarget();
        target.resize(main.width, main.height);

        Camera camera = client.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        camera.getViewRotationMatrix(viewRotation);
        projection.viewProjection(camera.getFov(), levelProjection.fold(), viewRotation, main.width, main.height,
                farViewProjection);
        FarProjection.gameViewProjection(levelProjection.projection(), viewRotation, gameViewProjection);

        Settings settings = SettingsService.get().settings();
        tree.frame(new CameraFrame(eye.x, eye.y, eye.z, new Matrix4f(farViewProjection),
                FarProjection.focalPixels(client.options.fov().get(), main.height), settings.farRenderCells(),
                settings.subdivisionSize(), arena.refused() > 0));

        commands.write(renderList, arena, runtime.frame(), eye.x, eye.y, eye.z);

        if (commands.count() > 0) {
            GpuBufferSlice written = indirect.write(commands);
            opaque.draw(target, arena, models, client.gameRenderer.lightmap(), written, commands.count(),
                    farViewProjection, runtime.frame().minBlockY());
            composite.draw(target, main, farViewProjection, gameViewProjection);
        }
    }

    public void describe(BlockPos pos) {
        RenderSystem.assertOnRenderThread();
        long[] keys = new long[DetailLevel.COUNT];

        for (int level = DetailLevel.MAX; level >= DetailLevel.MIN; level--) {
            long key = runtime.frame().keyAt(level, pos.getX(), pos.getY(), pos.getZ());
            keys[level] = key;
            MeshSlot slot = arena.slot(key);
            Eminus.LOGGER.info("{} slot level={} x={} y={} z={} present={} quads={} block={}", PROBE, level,
                    CellKey.x(key), CellKey.y(key), CellKey.z(key), slot == null ? 0 : 1,
                    slot == null ? 0 : slot.quads(), slot == null ? -1 : slot.block());
        }

        tree.describe(keys);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        stopped = true;
        runtime.stopListening();
        tree.stop();
        meshes.drop();
        baking.stop();

        indirect.close();
        composite.close();
        opaque.close();
        target.close();
        models.close();
        arena.close();
        Eminus.LOGGER.info("Far renderer stopped for {}", runtime.identity().dimension());
    }

    private final class Builds implements TreeBuilds {
        @Override
        public void build(long key, @Nullable CellHandle handle, int references) {
            meshes.request(key, handle, references);
        }

        @Override
        public void release(CellHandle handle, int references) {
            meshes.release(handle, references);
        }

        @Override
        public int backlog() {
            return meshes.backlog();
        }
    }
}
