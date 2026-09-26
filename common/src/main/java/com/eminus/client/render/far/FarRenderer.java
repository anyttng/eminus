package com.eminus.client.render.far;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import com.eminus.Eminus;
import com.eminus.api.v1.ArenaState;
import com.eminus.api.v1.FarLayerState;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.cache.CellHandle;
import com.eminus.handoff.NearPlane;
import com.eminus.handoff.NearReach;
import com.eminus.ingest.IngestService;
import com.eminus.handoff.NearSections;
import com.eminus.client.handoff.NearMaskPass;
import com.eminus.client.handoff.NearSectionTable;
import com.eminus.mesh.BakeryModels;
import com.eminus.mesh.BakeryTints;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshService;
import com.eminus.model.ModelIndex;
import com.eminus.client.model.ClientBakery;
import com.eminus.gpu.Capabilities;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.texture.Texture;
import com.eminus.render.arena.ArenaSizing;
import com.eminus.render.arena.MeshSlot;
import com.eminus.client.render.arena.GeometryArena;
import com.eminus.render.backend.BackendSupport;
import com.eminus.client.render.backend.BackendCheck;
import com.eminus.render.far.CompositeFog;
import com.eminus.render.far.DrawCommands;
import com.eminus.render.far.MeshOrder;
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

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

public final class FarRenderer implements AutoCloseable {
    public static final int START_COMMANDS = 32768;
    public static final String ARENA_CAP_PROPERTY = "eminus.arena.maxMiB";
    public static final String ARENA_FLOOR_PROPERTY = "eminus.arena.minMiB";

    private static final long BYTES_PER_MIB = 1L << 20;

    private final Gpu gpu;
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
    private final DrawCommands commands = new DrawCommands(START_COMMANDS);
    private final MeshOrder order = new MeshOrder();
    private final FarProjection projection = new FarProjection();
    private final LevelProjection levelProjection = new LevelProjection();
    private final Matrix4f farViewProjection = new Matrix4f();
    private final Matrix4f gameViewProjection = new Matrix4f();
    private final Matrix4f viewRotation = new Matrix4f();
    private final TreeManager tree;

    private IndirectCommands indirect;
    private volatile MeshService meshes;
    private RenderList renderList = RenderList.EMPTY;
    private @Nullable TreeBatch uploading;
    private int uploaded;
    private boolean stopped;

    private FarRenderer(Gpu gpu, DimensionRuntime runtime, ClientBakery baking, ModelPublisher models,
            GeometryArena arena, FarTarget target, FarFrame frame, NearMaskPass mask, NearSectionTable nearSections,
            OpaquePass opaque, OcclusionPass occlusion, TranslucentPass translucent, CompositePass composite,
            IndirectCommands indirect, int heightCells) {
        this.gpu = gpu;
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

    public static @Nullable FarRenderer start(Minecraft client, Gpu gpu, EminusInstance instance,
            DimensionRuntime runtime, int levelHeight, Settings settings, long replacedArenaBytes) {
        gpu.assertRenderThread();

        Texture main = gpu.mainColour();
        long ceiling = ceiling(gpu.capabilities(), replacedArenaBytes);
        long bytes = ArenaSizing.fitted(
                bounded(ArenaSizing.wanted(settings.farRenderCells(), settings.detailDistance().pixels(),
                        FarProjection.focalPixels(client.options.fov().get(), main.height()),
                        runtime.lowestStoredLevel()), ceiling),
                ceiling);
        BackendSupport support = BackendCheck.run(gpu, bytes);
        GeometryArena arena = GeometryArena.create(gpu, support, bytes);
        if (arena == null) {
            gpu.close();
            return null;
        }

        NearMaskPass mask = NearMaskPass.create(gpu, FarTarget.COLOUR_FORMAT, support.depth());
        OpaquePass opaque = OpaquePass.create(gpu, support.depth());
        OcclusionPass occlusion = OcclusionPass.create(gpu, support.depth());
        TranslucentPass translucent = TranslucentPass.create(gpu, support.depth());
        CompositePass composite = CompositePass.create(gpu, support.depth());
        Identifier refused = refusedProgram(List.of(mask.pipeline(), opaque.pipeline(), occlusion.pipeline(),
                translucent.pipeline(), composite.pipeline()));
        if (refused != null) {
            Eminus.LOGGER.warn("Renderer disabled: program {} did not compile", refused);
            composite.close();
            occlusion.close();
            arena.close();
            gpu.close();
            return null;
        }

        ClientBakery baking = ClientBakery.start(client);
        FarRenderer renderer = new FarRenderer(gpu, runtime, baking,
                ModelPublisher.start(gpu, baking.bakery()), arena,
                FarTarget.create(gpu, support.depthFormat(), main.width(), main.height()), FarFrame.create(gpu),
                mask, NearSectionTable.create(gpu), opaque, occlusion, translucent, composite,
                IndirectCommands.create(gpu, START_COMMANDS),
                Math.ceilDiv(levelHeight, FarDistance.BLOCKS_PER_TOP_LEVEL_CELL));

        renderer.meshes = new MeshService(instance.build(), runtime.cells(), runtime.coverage(), runtime.frame(),
                new BakeryModels(new ModelIndex(runtime.states(), baking.bakery()), baking.bakery()),
                new BakeryTints(baking.colours(), runtime.biomes()), client.options.biomeBlendRadius().get(),
                baking.opacity(runtime.states()), renderer.tree);
        runtime.listenTo(renderer.tree);
        Eminus.LOGGER.info("Far renderer started for {}", runtime.identity().dimension());

        return renderer;
    }

    static @Nullable Identifier refusedProgram(List<Pipeline> pipelines) {
        for (Pipeline pipeline : pipelines) {
            if (!pipeline.compiles()) {
                return pipeline.location();
            }
        }

        return null;
    }

    private static long ceiling(Capabilities device, long replacedArenaBytes) {
        OptionalLong texelBytes = device.texelElements().isPresent()
                ? OptionalLong.of(device.texelElements().getAsLong() * ArenaSizing.QUAD_BYTES)
                : OptionalLong.empty();
        OptionalLong freeBytes = device.freeBytes().isPresent()
                ? OptionalLong.of(device.freeBytes().getAsLong() + replacedArenaBytes)
                : OptionalLong.empty();
        long maxAllocation = device.maxAllocationBytes();
        long ceiling = ArenaSizing.ceiling(texelBytes, maxAllocation, freeBytes);

        Eminus.LOGGER.info("Geometry arena ceiling {} MiB: texel buffer {}, vertex index {} MiB, device share {}, "
                + "free video memory {}", ceiling / BYTES_PER_MIB,
                texelBytes.isPresent() ? texelBytes.getAsLong() / BYTES_PER_MIB + " MiB"
                        : "unread, " + ArenaSizing.UNREAD_TEXEL_BYTES / BYTES_PER_MIB + " MiB in its place",
                ArenaSizing.VERTEX_INDEX_BYTES / BYTES_PER_MIB,
                maxAllocation == Long.MAX_VALUE ? "unbounded"
                        : maxAllocation / ArenaSizing.DEVICE_SHARE / BYTES_PER_MIB + " MiB",
                freeBytes.isPresent() ? freeBytes.getAsLong() / BYTES_PER_MIB + " MiB with the replaced arena's "
                        + replacedArenaBytes / BYTES_PER_MIB + " MiB, "
                        + freeBytes.getAsLong() / ArenaSizing.FREE_MEMORY_SHARE / BYTES_PER_MIB + " MiB taken"
                        : "not reported");
        return ceiling;
    }

    private static long bounded(long wanted, long ceiling) {
        long bytes = wanted;
        Long floorMiB = Long.getLong(ARENA_FLOOR_PROPERTY);
        if (floorMiB != null) {
            Eminus.LOGGER.info("Geometry arena raised to at least {} MiB by -D{}, {} MiB wanted", floorMiB,
                    ARENA_FLOOR_PROPERTY, wanted / BYTES_PER_MIB);
            bytes = Math.max(bytes, floorMiB * BYTES_PER_MIB);
            if (bytes > ceiling) {
                Eminus.LOGGER.info("Geometry arena floor of {} MiB is above the device ceiling: the arena starts at "
                        + "{} MiB", floorMiB, ceiling / BYTES_PER_MIB);
            }
        }

        Long capMiB = Long.getLong(ARENA_CAP_PROPERTY);
        if (capMiB != null) {
            Eminus.LOGGER.info("Geometry arena capped at {} MiB by -D{}, {} MiB wanted", capMiB, ARENA_CAP_PROPERTY,
                    wanted / BYTES_PER_MIB);
            bytes = Math.min(bytes, capMiB * BYTES_PER_MIB);
        }

        return bytes;
    }

    public long arenaBytes() {
        return arena.state().bytes();
    }

    public static boolean recreates(Settings built, Settings updated) {
        return built.farRenderCells() != updated.farRenderCells()
                || built.detailDistance() != updated.detailDistance();
    }

    public void captureLevelProjection(Matrix4fc levelProjection, Matrix4fc cameraProjection) {
        this.levelProjection.capture(levelProjection, cameraProjection);
    }

    public boolean underPressure() {
        return !stopped && arena.pressure();
    }

    public boolean covers(FogData gameFog, int renderDistanceChunks) {
        if (stopped) {
            return false;
        }

        return !CompositeFog.skipped(gameFog.environmentalEnd, renderDistanceChunks * FarDistance.BLOCKS_PER_CHUNK);
    }

    public void frame(Minecraft client) {
        gpu.assertRenderThread();
        if (stopped) {
            return;
        }

        TreeBatch batch = tree.batches().peek();
        if (batch != null) {
            upload(batch);
        }

        models.publish();

        Texture main = gpu.mainColour();
        target.resize(main.width(), main.height());

        int renderDistance = client.options.getEffectiveRenderDistance();
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        camera.getViewRotationMatrix(viewRotation);
        projection.viewProjection(NearPlane.blocks(renderDistance), camera.getFov(), levelProjection.fold(),
                viewRotation, main.width(), main.height(), farViewProjection);
        FarProjection.gameViewProjection(levelProjection.projection(), viewRotation, gameViewProjection);

        Settings settings = SettingsService.get().settings();
        tree.frame(new CameraFrame(eye.x, eye.y, eye.z, new Matrix4f(farViewProjection),
                FarProjection.focalPixels(client.options.fov().get(), main.height()),
                FarProjection.focalPixels(camera.getFov(), main.height()), settings.farRenderCells(),
                settings.detailDistance().pixels(), arena.pressure()));

        FogData gameFog = client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.fogData;
        float nearBlocks = renderDistance * FarDistance.BLOCKS_PER_CHUNK;
        ClientLevel level = client.level;
        float reachBlocks = NearReach.blocks(renderDistance + ClientSession.CLIENT_EXTRA_CHUNKS, eye.y,
                level.getMinY(), level.getMinY() + level.getHeight());
        CompositeFog fog = CompositeFog.of(settings.fog(), settings.fade(), gameFog.environmentalStart,
                gameFog.environmentalEnd, nearBlocks, reachBlocks, settings.farRenderCells());
        if (fog.skip()) {
            return;
        }

        order.update(renderList, runtime.frame(), eye.x, eye.y, eye.z);
        commands.write(order.meshes(), order.translucent(), renderList::borderFaces, arena, runtime.frame(), eye.x,
                eye.y, eye.z);

        if (commands.count() > 0) {
            if (indirect.capacity() < commands.capacity()) {
                indirect.close();
                indirect = IndirectCommands.create(gpu, commands.capacity());
            }

            indirect.write(commands);
            fillNearSections(client, renderDistance, eye);

            frame.write(farViewProjection, runtime.frame().minBlockY(), models.atlas().cellsPerSide(),
                    nearSections.sections(), level.cardinalLighting());
            Texture mainDepth = gpu.mainDepth();
            Texture lightmap = gpu.lightmap();
            // On 26.2's Vulkan backend a shared index buffer grown inside a pass uploads nothing, and the GUI draws blank.
            FarQuads.reserveIndices();
            mask.draw(target.depth(), target.colour(), mainDepth);
            opaque.draw(target, arena, models, lightmap, indirect.buffer(), 0, commands.opaqueCount(),
                    frame.buffer(), nearSections.texels());
            if (client.options.ambientOcclusion().get()) {
                occlusion.draw(target, mainDepth, farViewProjection, gameViewProjection);
            }
            translucent.draw(target, arena, models, lightmap, indirect.buffer(), commands.opaqueCount(),
                    commands.translucentCount(), frame.buffer(), nearSections.texels());
            composite.draw(target, main, mainDepth, farViewProjection, gameViewProjection, fog, gameFog.color);
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
        gpu.assertRenderThread();
        String dimension = runtime.identity().dimension();
        ArenaState arenaState = arena.state();
        IngestService ingest = runtime.ingest();
        int ingestQueued = ingest == null ? 0 : ingest.queued();
        int pendingBlockChanges = ingest == null ? 0 : ingest.pendingBlockChanges();

        return tree.snapshot().thenApply(treeState ->
                new FarLayerState(dimension, arenaState, treeState, ingestQueued, pendingBlockChanges));
    }

    public CompletableFuture<List<long[]>> describe(int blockX, int blockY, int blockZ) {
        gpu.assertRenderThread();
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
        gpu.assertRenderThread();
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
        gpu.close();
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
        public void build(long key, @Nullable CellHandle handle, int references, long request, float priority) {
            meshes.request(key, handle, references, request, priority);
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
