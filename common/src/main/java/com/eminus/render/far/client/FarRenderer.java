package com.eminus.render.far.client;

import com.eminus.Eminus;
import com.eminus.cell.cache.CellHandle;
import com.eminus.mesh.BakeryModels;
import com.eminus.mesh.MeshService;
import com.eminus.model.ModelIndex;
import com.eminus.model.client.ClientBakery;
import com.eminus.render.arena.ArenaSizing;
import com.eminus.render.arena.client.GeometryArena;
import com.eminus.render.backend.BackendSupport;
import com.eminus.render.backend.client.BackendCheck;
import com.eminus.render.far.DrawCommands;
import com.eminus.render.tree.TreeBatch;
import com.eminus.render.tree.TreeManager;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;
import com.eminus.settings.SettingsService;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class FarRenderer implements AutoCloseable {
    public static final int COMMAND_CAPACITY = 32768;

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
    private final Matrix4f farViewProjection = new Matrix4f();
    private final Matrix4f gameViewProjection = new Matrix4f();
    private final TreeManager tree;

    private volatile MeshService meshes;
    private boolean stopped;

    private FarRenderer(DimensionRuntime runtime, ClientBakery baking, ModelPublisher models, GeometryArena arena,
            FarTarget target, OpaquePass opaque, CompositePass composite, IndirectCommands indirect) {
        this.runtime = runtime;
        this.baking = baking;
        this.models = models;
        this.arena = arena;
        this.target = target;
        this.opaque = opaque;
        this.composite = composite;
        this.indirect = indirect;
        tree = TreeManager.start(this::build);
    }

    public static @Nullable FarRenderer start(Minecraft client, EminusInstance instance, DimensionRuntime runtime) {
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
                IndirectCommands.create(COMMAND_CAPACITY));

        renderer.meshes = new MeshService(instance.build(), runtime.cells(),
                new BakeryModels(new ModelIndex(runtime.states(), baking.bakery()), baking.bakery()),
                runtime.states(), renderer.tree);
        runtime.listenTo(renderer.tree);
        Eminus.LOGGER.info("Far renderer started for {}", runtime.identity().dimension());

        return renderer;
    }

    public void frame(Minecraft client) {
        RenderSystem.assertOnRenderThread();
        if (stopped) {
            return;
        }

        models.publish();

        TreeBatch batch = tree.batches().take();
        if (batch != null) {
            arena.accept(batch.meshes());
        }

        RenderTarget main = client.gameRenderer.mainRenderTarget();
        target.resize(main.width, main.height);

        Camera camera = client.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        projection.viewProjection(camera, main.width, main.height, farViewProjection);
        camera.getViewRotationProjectionMatrix(gameViewProjection);
        commands.write(tree.renderList(), arena, runtime.frame(), eye.x, eye.y, eye.z);

        if (commands.count() > 0) {
            GpuBufferSlice written = indirect.write(commands);
            opaque.draw(target, arena, models, client.gameRenderer.lightmap(), written, commands.count(),
                    farViewProjection, runtime.frame().minBlockY());
            composite.draw(target, main, farViewProjection, gameViewProjection);
        }
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

    private void build(long key, @Nullable CellHandle handle, int references) {
        meshes.request(key, handle, references);
    }
}
