package com.eminus.client.render.far;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.eminus.Eminus;
import com.eminus.api.v1.ArenaState;
import com.eminus.api.v1.FarLayerState;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.cache.CellHandle;
import com.eminus.handoff.NearPlane;
import com.eminus.ingest.IngestService;
import com.eminus.handoff.NearSections;
import com.eminus.client.handoff.NearMaskPass;
import com.eminus.client.handoff.NearSectionTable;
import com.eminus.mesh.BakeryModels;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshService;
import com.eminus.model.ModelIndex;
import com.eminus.client.model.ClientBakery;
import com.eminus.render.arena.ArenaSizing;
import com.eminus.render.arena.MeshSlot;
import com.eminus.client.render.arena.GeometryArena;
import com.eminus.render.backend.BackendSupport;
import com.eminus.client.render.backend.BackendCheck;
import com.eminus.render.far.CompositeFog;
import com.eminus.render.far.DrawCommands;
import com.eminus.render.far.TranslucentOrder;
import com.eminus.render.tree.CameraFrame;
import com.eminus.render.tree.NodeRow;
import com.eminus.render.tree.RenderList;
import com.eminus.render.tree.TreeBatch;
import com.eminus.render.tree.TreeBuilds;
import com.eminus.render.tree.TreeExtent;
import com.eminus.render.tree.TreeManager;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;
import com.eminus.client.session.ClientSession;
import com.eminus.settings.FarDistance;
import com.eminus.settings.Settings;
import com.eminus.settings.SettingsService;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

public final class FarRenderer implements AutoCloseable {
    public static final int COMMAND_CAPACITY = 32768;
    public static final String ARENA_CAP_PROPERTY = "eminus.arena.maxMiB";

    private static final long BYTES_PER_MIB = 1L << 20;

    private final DimensionRuntime runtime;
    private final ClientBakery baking;
    private final ModelPublisher models;
    private final GeometryArena arena;
    private final FarTarget target;
    private final FarFrame frame;
    private final NearMaskPass mask;
    private final NearSectionTable nearSections;
    private final OpaquePass opaque;
    private final OcclusionPass occlusion;
    private final TranslucentPass translucent;
    private final CompositePass composite;
    private final IndirectCommands indirect;
    private final DrawCommands commands = new DrawCommands(COMMAND_CAPACITY);
    private final TranslucentOrder order = new TranslucentOrder();
    private final FarProjection projection = new FarProjection();
    private final LevelProjection levelProjection = new LevelProjection();
    private final Matrix4f farViewProjection = new Matrix4f();
    private final Matrix4f gameViewProjection = new Matrix4f();
    private final Matrix4f viewRotation = new Matrix4f();
    private final TreeManager tree;

    private volatile MeshService meshes;
    private RenderList renderList = RenderList.EMPTY;
    private @Nullable TreeBatch uploading;
    private int uploaded;
    private boolean stopped;

    private FarRenderer(DimensionRuntime runtime, ClientBakery baking, ModelPublisher models, GeometryArena arena,
            FarTarget target, FarFrame frame, NearMaskPass mask, NearSectionTable nearSections, OpaquePass opaque,
            OcclusionPass occlusion, TranslucentPass translucent, CompositePass composite, IndirectCommands indirect,
            int heightCells) {
        this.runtime = runtime;
        this.baking = baking;
        this.models = models;
        this.arena = arena;
        this.target = target;
        this.frame = frame;
        this.mask = mask;
        this.nearSections = nearSections;
        this.opaque = opaque;
        this.occlusion = occlusion;
        this.translucent = translucent;
        this.composite = composite;
        this.indirect = indirect;
        tree = TreeManager.start(new Builds(),
                new TreeExtent(runtime.frame(), heightCells, runtime.lowestStoredLevel()));
    }

    public static @Nullable FarRenderer start(Minecraft client, EminusInstance instance, DimensionRuntime runtime,
            int levelHeight, Settings settings) {
        RenderSystem.assertOnRenderThread();

        RenderTarget main = client.gameRenderer.mainRenderTarget();
        long bytes = ArenaSizing.fitted(
                capped(ArenaSizing.wanted(settings.farRenderCells(), settings.detailDistance().pixels(),
                        FarProjection.focalPixels(client.options.fov().get(), main.height),
                        runtime.lowestStoredLevel())),
                RenderSystem.getDevice().getDeviceInfo().limits().maxMemoryAllocationSize());
        BackendSupport support = BackendCheck.run(bytes);
        GeometryArena arena = GeometryArena.create(support, bytes);
        if (arena == null) {
            return null;
        }

        ClientBakery baking = ClientBakery.start(client);
        FarRenderer renderer = new FarRenderer(runtime, baking,
                ModelPublisher.start(baking.bakery(), baking.colours(), runtime.biomes()), arena,
                FarTarget.create(support.depthStencilFormat(), main.width, main.height), FarFrame.create(),
                NearMaskPass.create(FarTarget.COLOUR_FORMAT), NearSectionTable.create(),
                OpaquePass.create(support.depth()), OcclusionPass.create(support.depth()),
                TranslucentPass.create(support.depth()), CompositePass.create(support.depth()),
                IndirectCommands.create(COMMAND_CAPACITY),
                Math.ceilDiv(levelHeight, FarDistance.BLOCKS_PER_TOP_LEVEL_CELL));

        renderer.meshes = new MeshService(instance.build(), runtime.cells(), runtime.coverage(),
                new BakeryModels(new ModelIndex(runtime.states(), baking.bakery()), baking.bakery()),
                baking.opacity(runtime.states()), renderer.tree);
        runtime.listenTo(renderer.tree);
        Eminus.LOGGER.info("Far renderer started for {}", runtime.identity().dimension());

        return renderer;
    }

    private static long capped(long wanted) {
        Long capMiB = Long.getLong(ARENA_CAP_PROPERTY);
        if (capMiB == null) {
            return wanted;
        }

        Eminus.LOGGER.info("Geometry arena capped at {} MiB by -D{}, {} MiB wanted", capMiB, ARENA_CAP_PROPERTY,
                wanted / BYTES_PER_MIB);
        return Math.min(wanted, capMiB * BYTES_PER_MIB);
    }

    public static boolean recreates(Settings built, Settings updated) {
        return built.farRenderCells() != updated.farRenderCells()
                || built.detailDistance() != updated.detailDistance();
    }

    public void captureLevelProjection(Matrix4fc levelProjection, Matrix4fc cameraProjection) {
        this.levelProjection.capture(levelProjection, cameraProjection);
    }

    public boolean covers(FogData gameFog, int renderDistanceChunks) {
        if (stopped) {
            return false;
        }

        return !CompositeFog.skipped(gameFog.environmentalEnd, renderDistanceChunks * FarDistance.BLOCKS_PER_CHUNK);
    }

    public void frame(Minecraft client) {
        RenderSystem.assertOnRenderThread();
        if (stopped) {
            return;
        }

        models.publish();

        TreeBatch batch = tree.batches().peek();
        if (batch != null) {
            upload(batch);
        }

        RenderTarget main = client.gameRenderer.mainRenderTarget();
        target.resize(main.width, main.height);

        int renderDistance = client.options.getEffectiveRenderDistance();
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        camera.getViewRotationMatrix(viewRotation);
        projection.viewProjection(NearPlane.blocks(renderDistance), camera.getFov(), levelProjection.fold(),
                viewRotation, main.width, main.height, farViewProjection);
        FarProjection.gameViewProjection(levelProjection.projection(), viewRotation, gameViewProjection);

        Settings settings = SettingsService.get().settings();
        float focalPixels = FarProjection.focalPixels(client.options.fov().get(), main.height);
        tree.frame(new CameraFrame(eye.x, eye.y, eye.z, new Matrix4f(farViewProjection), focalPixels,
                settings.farRenderCells(), settings.detailDistance().pixels(), arena.pressure()));

        FogData gameFog = client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.fogData;
        float nearBlocks = renderDistance * FarDistance.BLOCKS_PER_CHUNK;
        CompositeFog fog = CompositeFog.of(settings.fog(), settings.fade(), gameFog.environmentalStart,
                gameFog.environmentalEnd, nearBlocks, settings.farRenderCells());
        if (fog.skip()) {
            return;
        }

        order.update(renderList, runtime.frame(), eye.x, eye.y, eye.z);
        commands.write(renderList, order.meshes(), arena, runtime.frame(), eye.x, eye.y, eye.z);

        if (commands.count() > 0) {
            indirect.write(commands);
            if (commands.translucentCount() > 0) {
                fillNearSections(client, renderDistance, eye);
            }

            frame.write(farViewProjection, runtime.frame().minBlockY(), models.atlas().cellsPerSide(),
                    nearSections.sections());
            mask.draw(target.maskView(), target.colourView(), target.width(), target.height(),
                    main.getDepthTextureView());
            opaque.draw(target, arena, models, client.gameRenderer.lightmap(),
                    indirect.range(0, commands.opaqueCount()), commands.opaqueCount(), frame.buffer(),
                    nearSections.buffer());
            if (client.options.ambientOcclusion().get()) {
                occlusion.draw(target, main, farViewProjection, gameViewProjection);
            }
            translucent.draw(target, arena, models, client.gameRenderer.lightmap(),
                    indirect.range(commands.opaqueCount(), commands.translucentCount()),
                    commands.translucentCount(), frame.buffer(), nearSections.buffer());
            composite.draw(target, main, farViewProjection, gameViewProjection, fog, gameFog.color);
        }
    }

    private void upload(TreeBatch batch) {
        if (batch != uploading) {
            uploading = batch;
            uploaded = 0;
            arena.evict(batch.evicted());
        }

        List<CellMesh> batchMeshes = batch.meshes();
        uploaded = arena.accept(batchMeshes, uploaded);
        if (uploaded < batchMeshes.size()) {
            return;
        }

        RenderList walked = batch.renderList();
        if (walked != null) {
            renderList = walked;
        }

        tree.batches().take();
        uploading = null;
    }

    public CompletableFuture<FarLayerState> state() {
        RenderSystem.assertOnRenderThread();
        String dimension = runtime.identity().dimension();
        ArenaState arenaState = arena.state();
        IngestService ingest = runtime.ingest();
        int ingestQueued = ingest == null ? 0 : ingest.queued();
        int pendingBlockChanges = ingest == null ? 0 : ingest.pendingBlockChanges();

        return tree.snapshot().thenApply(treeState ->
                new FarLayerState(dimension, arenaState, treeState, ingestQueued, pendingBlockChanges));
    }

    public CompletableFuture<List<long[]>> describe(int blockX, int blockY, int blockZ) {
        RenderSystem.assertOnRenderThread();
        List<long[]> rows = new ArrayList<>(DetailLevel.COUNT);

        for (int level = DetailLevel.MIN; level <= DetailLevel.MAX; level++) {
            long key = runtime.frame().keyAt(level, blockX, blockY, blockZ);
            MeshSlot slot = arena.slot(key);
            long[] row = new long[NodeRow.WIDTH];
            row[NodeRow.LEVEL] = level;
            row[NodeRow.CELL_X] = CellKey.x(key);
            row[NodeRow.CELL_Y] = CellKey.y(key);
            row[NodeRow.CELL_Z] = CellKey.z(key);
            row[NodeRow.SLOT_PRESENT] = slot == null ? 0 : 1;
            row[NodeRow.SLOT_QUADS] = slot == null ? 0 : slot.quads();
            row[NodeRow.SLOT_BLOCK] = slot == null ? -1 : slot.block();
            rows.add(row);
        }

        return tree.describe(rows);
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
        occlusion.close();
        nearSections.close();
        frame.close();
        target.close();
        models.close();
        arena.close();
        Eminus.LOGGER.info("Far renderer stopped for {}", runtime.identity().dimension());
    }

    private void fillNearSections(Minecraft client, int renderDistance, Vec3 eye) {
        ClientLevel level = client.level;
        nearSections.fill(client.levelRenderer, order.meshes(), runtime.frame(),
                NearSections.section(Mth.floor(eye.x)), NearSections.section(Mth.floor(eye.y)),
                NearSections.section(Mth.floor(eye.z)), renderDistance,
                renderDistance + ClientSession.CLIENT_EXTRA_CHUNKS, level.getMinSectionY(), level.getSectionsCount());
    }

    private final class Builds implements TreeBuilds {
        @Override
        public void build(long key, @Nullable CellHandle handle, int references, long request) {
            meshes.request(key, handle, references, request);
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
