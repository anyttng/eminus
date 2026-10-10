package com.eminus.client.render.far;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToIntFunction;

import com.eminus.Eminus;
import com.eminus.box.BoxRegistry;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.cache.CellHandle;
import com.eminus.handoff.NearPlane;
import com.eminus.handoff.NearReach;
import com.eminus.ingest.IngestService;
import com.eminus.handoff.NearSections;
import com.eminus.client.handoff.NearMaskPass;
import com.eminus.client.handoff.NearSectionTable;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshService;
import com.eminus.client.mesh.MeshWiring;
import com.eminus.client.model.ClientBakery;
import com.eminus.client.model.ModelPublisher;
import com.eminus.client.frame.GameFog;
import com.eminus.client.frame.GameFrame;
import com.eminus.client.frame.GameFrames;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.Location;
import com.eminus.gpu.Sparse;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.texture.Texture;
import com.eminus.render.arena.ArenaSizing;
import com.eminus.render.arena.MeshSlot;
import com.eminus.client.render.arena.ArenaState;
import com.eminus.client.render.arena.GeometryArena;
import com.eminus.render.backend.BackendSupport;
import com.eminus.render.backend.DepthConvention;
import com.eminus.client.render.backend.BackendCheck;
import com.eminus.client.render.box.BoxPass;
import com.eminus.render.far.CameraOrigin;
import com.eminus.render.far.CompositeFog;
import com.eminus.render.far.DrawCommands;
import com.eminus.render.far.MeshOrder;
import com.eminus.render.far.TurnMargin;
import com.eminus.render.tree.CameraFrame;
import com.eminus.render.tree.NodeRow;
import com.eminus.render.tree.NodeTable;
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

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

public final class FarRenderer implements AutoCloseable {
    public static final int START_COMMANDS = 32768;
    public static final String ARENA_CAP_PROPERTY = "eminus.arena.maxMiB";
    public static final String ARENA_FLOOR_PROPERTY = "eminus.arena.minMiB";

    private static final String PROGRAM_REFUSED = "program %s did not compile";

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
    private final BoxPass boxes;
    private final DrawCommands commands = new DrawCommands(START_COMMANDS);
    private final MeshOrder order = new MeshOrder();
    private final FarProjection projection = new FarProjection();
    private final LevelProjection levelProjection = new LevelProjection();
    private final Matrix4f farProjection = new Matrix4f();
    private final Matrix4f farViewProjection = new Matrix4f();
    private final Matrix4f gameViewProjection = new Matrix4f();
    private final FrustumIntersection drawFrustum = new FrustumIntersection();
    private final TurnMargin turnMargin = new TurnMargin();
    private final TreeManager tree;
    private final FarDraw draw;
    private final FarShadow shadow;

    private IndirectCommands indirect;
    private CompositeFog fog;
    private Vector4fc fogColour;
    private volatile MeshService meshes;
    private RenderList renderList = RenderList.EMPTY;
    private @Nullable TreeBatch uploading;
    private @Nullable GameFrame game;
    private int uploaded;
    private long frames;
    private boolean framePending = true;
    private boolean farFrame;
    private boolean terrainDrawn;
    private boolean stopped;

    private FarRenderer(Gpu gpu, DepthConvention depth, DimensionRuntime runtime, ClientBakery baking,
            ModelPublisher models, GeometryArena arena, FarTarget target, FarFrame frame, NearMaskPass mask,
            NearSectionTable nearSections, OpaquePass opaque, OcclusionPass occlusion, TranslucentPass translucent,
            CompositePass composite, BoxPass boxes, IndirectCommands indirect, int heightCells, int nodeCapacity) {
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
        this.boxes = boxes;
        this.indirect = indirect;
        draw = new FarDraw(gpu, depth, baking.variantDraw(), arena, models, frame, nearSections, target);
        shadow = FarShadow.create(gpu);
        tree =TreeManager.start(new Builds(),
                new TreeExtent(runtime.frame(), heightCells, runtime.lowestStoredLevel()), nodeCapacity);
    }

    public static FarStart start(Minecraft client, Gpu gpu, EminusInstance instance,
            DimensionRuntime runtime, int levelHeight, Settings settings, long replacedArenaBytes,
            ToIntFunction<BlockState> packIds) {
        gpu.assertRenderThread();

        Texture main = gpu.mainColour();
        long ceiling = ArenaBudget.ceiling(gpu.capabilities(), replacedArenaBytes);
        float focal = FarProjection.focalPixels(client.options.fov().get(), main.height());
        @Nullable Sparse sparse = ArenaBudget.sparse(gpu);
        long bytes = ArenaBudget.bytes(ArenaSizing.wanted(settings.farRenderCells(),
                settings.detailDistance().pixels(), focal, runtime.lowestStoredLevel()), ceiling, sparse);
        BackendSupport support = BackendCheck.run(gpu, bytes);
        GeometryArena arena = GeometryArena.create(gpu, support, bytes, sparse);
        if (arena == null) {
            gpu.close();
            return FarStart.refused(support.reason());
        }

        ClientBakery baking = ClientBakery.start(client, packIds);
        NearMaskPass mask = NearMaskPass.create(gpu, FarTarget.COLOUR_FORMAT, support.depth(),
                CompositePass.DEPTH_BIAS);
        OpaquePass opaque = OpaquePass.create(gpu, support.depth(), baking.variantDraw());
        OcclusionPass occlusion = OcclusionPass.create(gpu, support.depth());
        TranslucentPass translucent = TranslucentPass.create(gpu, support.depth(), baking.variantDraw());
        CompositePass composite = CompositePass.create(gpu, support.depth());
        BoxPass boxes = BoxPass.create(gpu, support.depth());
        List<Pipeline> pipelines = new ArrayList<>(List.of(mask.pipeline(), opaque.pipeline(),
                occlusion.pipeline(), translucent.pipeline(), composite.pipeline()));
        pipelines.addAll(boxes.pipelines());
        Location refused = refusedProgram(pipelines);
        if (refused != null) {
            String refusal = PROGRAM_REFUSED.formatted(refused);
            Eminus.LOGGER.warn("Renderer disabled: {}", refusal);
            baking.stop();
            boxes.close();
            composite.close();
            occlusion.close();
            mask.close();
            arena.close();
            gpu.close();
            return FarStart.refused(refusal);
        }

        FarRenderer renderer = new FarRenderer(gpu, support.depth(), runtime, baking,
                ModelPublisher.start(gpu, baking.bakery()), arena,
                FarTarget.create(gpu, support.depthFormat(), main.width(), main.height()), FarFrame.create(gpu),
                mask, NearSectionTable.create(gpu), opaque, occlusion, translucent, composite, boxes,
                IndirectCommands.create(gpu, START_COMMANDS),
                Math.ceilDiv(levelHeight, FarDistance.BLOCKS_PER_TOP_LEVEL_CELL),
                NodeTable.capacity(runtime.lowestStoredLevel(), focal, settings.detailDistance().pixels(),
                        settings.farRenderCells(), levelHeight));

        renderer.meshes = MeshWiring.service(client, runtime, instance, baking, renderer.tree);
        runtime.listenTo(renderer.tree);
        Eminus.LOGGER.info("Far renderer started for {}", runtime.identity().dimension());

        return FarStart.started(renderer);
    }

    static @Nullable Location refusedProgram(List<Pipeline> pipelines) {
        for (Pipeline pipeline : pipelines) {
            if (!pipeline.compiles()) {
                return pipeline.location();
            }
        }

        return null;
    }

    public long arenaBytes() {
        return arena.committedBytes();
    }

    public static boolean recreates(Settings built, Settings updated) {
        return built.farRenderCells() != updated.farRenderCells()
                || built.detailDistance() != updated.detailDistance();
    }

    public void captureLevelProjection(Matrix4fc levelProjection, Matrix4fc cameraProjection) {
        this.levelProjection.capture(levelProjection, cameraProjection);
    }

    public boolean detailLimited() {
        return !stopped && (arena.pressure() || tree.horizonBounded());
    }

    public boolean covers(GameFog gameFog, int renderDistanceChunks) {
        if (stopped) {
            return false;
        }

        return !CompositeFog.skipped(gameFog.environmentalEnd(), renderDistanceChunks * FarDistance.BLOCKS_PER_CHUNK);
    }

    public void frame(Minecraft client) {
        if (!stepped(client) || game == null) {
            return;
        }

        Texture main = gpu.mainColour();
        Texture mainDepth = gpu.mainDepth();
        if (farFrame && (terrainDrawn || boxes.drawsAny())) {
            drawMask();
            Texture lightmap = gpu.lightmap();
            opaque.draw(target, arena, models, lightmap, indirect.buffer(), 0,
                    terrainDrawn ? commands.opaqueCount() : 0, frame.buffer(), nearSections.texels());
            if (terrainDrawn && client.options.ambientOcclusion().get()) {
                occlusion.draw(target, mainDepth, farViewProjection, gameViewProjection);
            }
            if (terrainDrawn) {
                translucent.draw(target, arena, models, lightmap, indirect.buffer(), commands.opaqueCount(),
                        commands.translucentCount(), frame.buffer(), nearSections.texels());
            }
            boxes.drawFar(target, farViewProjection, game);
            composite.draw(target, main, mainDepth, farViewProjection, gameViewProjection, fog, fogColour);
        }
        boxes.drawNear(main, mainDepth, gameViewProjection, game, NearPlane.blocks(game.renderDistance()));
    }

    public @Nullable FarDraw packFrame(Minecraft client) {
        if (!stepped(client) || !terrainDrawn) {
            return null;
        }

        drawMask();
        return draw;
    }

    public FarDraw farDraw() {
        return draw;
    }

    public @Nullable FarShadow shadowFrame(Minecraft client, Matrix4fc shadowView, Matrix4fc shadowProjection) {
        if (!stepped(client) || !terrainDrawn || game == null) {
            return null;
        }

        return shadow.write(order.meshes(), order.translucent(), renderList::borderFaces, arena, runtime.frame(),
                game, models.atlas().cellsPerSide(), nearSections.sections(), farViewProjection, shadowView,
                shadowProjection) ? shadow : null;
    }

    public void newFrame() {
        framePending = true;
    }

    private void drawMask() {
        mask.draw(target.depth(), target.colour(), gpu.mainDepth(), farViewProjection, gameViewProjection);
    }

    // The shadow pass runs before the main pass: a second step would upload a second batch and post a second walk.
    private boolean stepped(Minecraft client) {
        gpu.assertRenderThread();
        if (stopped) {
            return false;
        }

        if (framePending) {
            framePending = false;
            farFrame = false;
            terrainDrawn = false;
            stepFrame(client);
        }
        return true;
    }

    private void stepFrame(Minecraft client) {
        boxes.update(BoxRegistry.get(), runtime.identity().dimension());
        TreeBatch batch = tree.batches().peek();
        if (batch != null) {
            upload(batch);
        }

        models.publish();

        Texture main = gpu.mainColour();
        target.resize(main.width(), main.height());

        GameFrame game = GameFrames.read(client);
        this.game = game;
        int renderDistance = game.renderDistance();
        projection.projection(NearPlane.blocks(renderDistance), game.fov(), levelProjection.fold(), main.width(),
                main.height(), farProjection);
        farViewProjection.set(farProjection).mul(game.viewRotation());
        FarProjection.gameViewProjection(levelProjection.projection(), game.viewRotation(), gameViewProjection);

        frames++;
        Quaternionf rotation = game.viewRotation().getNormalizedRotation(new Quaternionf());
        float margin = turnMargin.frame(rotation);
        Matrix4f walkViewProjection = projection.walkViewProjection(NearPlane.blocks(renderDistance), game.fov(),
                margin, levelProjection.fold(), game.viewRotation(), main.width(), main.height(), new Matrix4f());

        Settings settings = SettingsService.get().settings();
        tree.frame(new CameraFrame(game.eyeX(), game.eyeY(), game.eyeZ(), walkViewProjection,
                FarProjection.focalPixels(client.options.fov().get(), main.height()),
                FarProjection.focalPixels(game.fov(), main.height()), settings.farRenderCells(),
                settings.detailDistance().pixels(), arena.pressure(), frames, rotation, margin));

        GameFog gameFog = game.fog();
        float nearBlocks = renderDistance * FarDistance.BLOCKS_PER_CHUNK;
        ClientLevel level = client.level;
        float reachBlocks = NearReach.blocks(renderDistance + ClientSession.CLIENT_EXTRA_CHUNKS, game.eyeY(),
                level.getMinY(), level.getMinY() + level.getHeight());
        fog = CompositeFog.of(settings.fog(), settings.fade(), gameFog.environmentalStart(),
                gameFog.environmentalEnd(), nearBlocks, reachBlocks, settings.farRenderCells());
        fogColour = gameFog.colour();
        if (fog.skip()) {
            return;
        }

        farFrame = true;
        order.update(renderList, runtime.frame(), game.eyeX(), game.eyeY(), game.eyeZ());
        commands.write(order.meshes(), order.translucent(), renderList::borderFaces, arena, runtime.frame(),
                drawFrustum.set(farViewProjection), game.eyeX(), game.eyeY(), game.eyeZ());
        if (commands.count() == 0) {
            return;
        }

        if (indirect.capacity() < commands.capacity()) {
            indirect.close();
            indirect = IndirectCommands.create(gpu, commands.capacity());
        }

        indirect.write(commands);
        fillNearSections(client, game);

        frame.write(farViewProjection, game.viewRotation(), runtime.frame().minBlockY(),
                models.atlas().cellsPerSide(), nearSections.sections(), game.shade(),
                CameraOrigin.of(game.eyeX(), game.eyeY(), game.eyeZ()));
        draw.frame(indirect.buffer(), commands.opaqueCount(), commands.translucentCount(), gpu.lightmap(),
                farProjection, game.viewRotation(), FarDistance.cellsToBlocks(settings.farRenderCells()));
        terrainDrawn = true;
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
            CameraFrame walkedWith = walked.walkedWith();
            if (walkedWith != null) {
                turnMargin.drawn(walkedWith);
            }
        }

        tree.batches().take();
        uploading = null;
    }

    public CompletableFuture<FarState> state() {
        gpu.assertRenderThread();
        String dimension = runtime.identity().dimension();
        ArenaState arenaState = arena.state();
        IngestService ingest = runtime.ingest();
        int ingestQueued = ingest == null ? 0 : ingest.queued();
        int pendingBlockChanges = ingest == null ? 0 : ingest.pendingBlockChanges();

        return tree.snapshot().thenApply(treeState ->
                new FarState(dimension, arenaState, treeState, ingestQueued, pendingBlockChanges));
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

    public CompletableFuture<Map<String, Long>> churn() {
        return tree.churn();
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
        shadow.close();
        boxes.close();
        composite.close();
        occlusion.close();
        mask.close();
        nearSections.close();
        frame.close();
        target.close();
        models.close();
        arena.close();
        gpu.close();
        Eminus.LOGGER.info("Far renderer stopped for {}", runtime.identity().dimension());
    }

    private void fillNearSections(Minecraft client, GameFrame game) {
        ClientLevel level = client.level;
        int renderDistance = game.renderDistance();
        nearSections.fill(client.levelRenderer, game.sectionFadeMillis(), order.meshes(), runtime.frame(),
                NearSections.section(Mth.floor(game.eyeX())), NearSections.section(Mth.floor(game.eyeY())),
                NearSections.section(Mth.floor(game.eyeZ())), renderDistance,
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
