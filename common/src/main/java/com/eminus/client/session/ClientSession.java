package com.eminus.client.session;

import java.nio.file.Path;
import java.util.BitSet;
import java.util.Objects;
import java.util.function.ToIntFunction;

import com.eminus.Eminus;
import com.eminus.beam.BeamSet;
import com.eminus.box.BoxRegistry;
import com.eminus.client.beam.BeamTriggers;
import com.eminus.compat.iris.IrisShaderPack;
import com.eminus.compat.iris.PackSlot;
import com.eminus.handoff.NearFieldOverride;
import com.eminus.ingest.IngestService;
import com.eminus.ingest.IngestTrigger;
import com.eminus.mixin.BiomeManagerAccessor;
import com.eminus.client.frame.GameFrame;
import com.eminus.client.frame.GameFrames;
import com.eminus.client.gpu.Gpus;
import com.eminus.client.model.ClientBakery;
import com.eminus.client.render.far.FarRenderer;
import com.eminus.client.render.far.FarStart;
import com.eminus.gpu.Gpu;
import com.eminus.model.PackBlocks;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;
import com.eminus.session.StoreFolders;
import com.eminus.session.WorldIdentity;
import com.eminus.settings.Settings;
import com.eminus.settings.SettingsService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

public final class ClientSession {
    public static final Identifier RELOAD_ID = Identifier.fromNamespaceAndPath(Eminus.MODID, "far_renderer");
    public static final int CLIENT_EXTRA_CHUNKS = 3;

    private static final String IN_SESSION = "reached only inside a session";
    private static final String WITH_LEVEL = "a renderer starts only with a runtime and a level";
    private static final String TESTED = "hasSingleplayerServer tests it";

    private static boolean heldChunksPending;
    private static boolean overShaderPack;
    private static boolean inShaderPack;
    private static boolean throughDhPrograms;
    private static int renderedBiomeBlend;
    private static @Nullable ToIntFunction<BlockState> renderedPackIds;
    private static PackBlocks renderedPackBlocks = PackBlocks.NONE;
    private static @Nullable EminusInstance instance;
    private static @Nullable DimensionRuntime runtime;
    private static @Nullable FarRenderer renderer;
    private static @Nullable String refusal;
    private static @Nullable Settings rendered;
    private static @Nullable ClientLevel level;
    private static String world = "";

    public static void login() {
        if (instance != null) {
            return;
        }

        IrisShaderPack.listenToShadowPass();
        Minecraft minecraft = Minecraft.getInstance();
        world = worldName(minecraft);
        Settings settings = SettingsService.get().settings();
        instance = EminusInstance.start(
                storeBase(minecraft),
                settings.workerThreads(),
                settings.lodQuality().storedLevel(),
                System::currentTimeMillis);
    }

    public static void disconnect() {
        BoxRegistry.get().clear();
        endSession();
    }

    private static void endSession() {
        if (instance == null) {
            return;
        }

        stopRenderer();
        level = null;
        runtime = null;
        instance.shutdown();
        instance = null;
    }

    public static void resourcesReloaded() {
        if (runtime != null) {
            restartRenderer();
        }
    }

    public static void settingsChanged(Settings updated) {
        IrisShaderPack.settingsChanged(updated);
        if (instance == null) {
            return;
        }

        if (updated.lodQuality().storedLevel() != instance.lowestStoredLevel()) {
            restartSession();
            return;
        }

        if (updated.workerThreads() != instance.workerThreads()) {
            instance.resizeWorkers(updated.workerThreads());
        }

        if (runtime == null || rendered == null) {
            return;
        }

        boolean resizes = FarRenderer.resizes(rendered, updated);
        if (FarRenderer.recreates(rendered, updated) || (resizes && renderer == null)) {
            restartRenderer();
        } else if (renderer != null) {
            if (resizes) {
                renderer.resize(Minecraft.getInstance(), updated);
            }
            renderer.retexture(updated.lodQuality());
            rendered = updated;
        }
    }

    public static void captureLevelProjection(Matrix4fc levelProjection, Matrix4fc cameraProjection) {
        if (renderer != null) {
            renderer.captureLevelProjection(levelProjection, cameraProjection);
        }
    }

    public static void drawFarLayer() {
        if (renderer != null && !overShaderPack) {
            renderer.frame(Minecraft.getInstance());
        }
    }

    public static void drawFarLayerOverShaderPack() {
        if (renderer != null && overShaderPack && !inShaderPack) {
            renderer.frame(Minecraft.getInstance());
        }
    }

    public static void drawFarLayerInShaderPack(Object pipeline, PackSlot slot) {
        if (renderer != null && inShaderPack) {
            IrisShaderPack.drawInPack(renderer, pipeline, slot);
        }
    }

    public static void drawFarLayerInShadowPass(Matrix4fc shadowView, Matrix4fc shadowProjection) {
        if (renderer != null && inShaderPack) {
            IrisShaderPack.drawShadow(renderer, shadowView, shadowProjection);
        }
    }

    public static void drawTranslucentFarLayerInShadowPass() {
        if (renderer != null && inShaderPack) {
            IrisShaderPack.drawShadowTranslucent(renderer);
        }
    }

    public static boolean drawsOverShaderPack() {
        return overShaderPack;
    }

    public static void overrideNearField() {
        Minecraft client = Minecraft.getInstance();
        if (renderer != null) {
            renderer.newFrame();
        }
        readShaderPack();
        if (renderer == null || client.level == null || overShaderPack) {
            NearFieldOverride.skip();
            return;
        }

        GameFrame game = GameFrames.read(client);
        if (renderer.covers(game.fog(), game.renderDistance())) {
            GameFrames.overrideNearField(client, game.cameraInAir() && !SettingsService.get().settings().fog());
            NearFieldOverride.apply();
        } else {
            NearFieldOverride.skip();
        }
    }

    private static void readShaderPack() {
        boolean inUse = IrisShaderPack.inUse();
        boolean inside = inUse && renderer != null && IrisShaderPack.packPathReady(renderer);
        boolean dhPrograms = inside && IrisShaderPack.throughDhPrograms();
        if (inUse != overShaderPack || inside != inShaderPack || dhPrograms != throughDhPrograms) {
            overShaderPack = inUse;
            inShaderPack = inside;
            throughDhPrograms = dhPrograms;
            Eminus.LOGGER.info(dhPrograms
                    ? "Shader pack has no Eminus contract: the far layer draws through its Distant Horizons programs"
                    : inside ? "Shader pack carries the Eminus contract: the far layer draws inside the pack"
                    : inUse ? "Shader pack in use: the far layer draws over the pack's finished frame"
                    : "No shader pack: the far layer draws in its own target");
        }
    }

    public static @Nullable EminusInstance instance() {
        return instance;
    }

    public static @Nullable DimensionRuntime runtime() {
        return runtime;
    }

    public static @Nullable FarRenderer renderer() {
        return renderer;
    }

    public static PackBlocks packBlocks() {
        return renderedPackBlocks;
    }

    public static @Nullable String refusal() {
        return refusal;
    }

    @SuppressWarnings("ReferenceEquality")
    public static void tick() {
        if (instance == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel current = minecraft.level;
        if (current != level) {
            swapLevel(current);
        }

        restartOnStaleInputs(minecraft);
        if (heldChunksPending) {
            submitHeldChunks();
        }

        ClientLevel ticking = level;
        if (ticking == null) {
            return;
        }

        IngestService ingest = ingestFor(ticking);
        if (ingest != null) {
            ingest.pollDebounce(ticking, System.currentTimeMillis());
        }
    }

    @SuppressWarnings("ReferenceEquality")
    private static void restartOnStaleInputs(Minecraft minecraft) {
        if (renderer != null && minecraft.options.biomeBlendRadius().get() != renderedBiomeBlend) {
            restartRenderer();
        }

        if (renderer != null && IrisShaderPack.packIds(renderedPackIds) != renderedPackIds) {
            Eminus.LOGGER.info("Shader pack block ids changed: the far layer rebuilds");
            restartRenderer();
        }

        if (renderer != null && !IrisShaderPack.movedIds().equals(renderedPackBlocks.moved())) {
            Eminus.LOGGER.info("Shader pack's moved blocks changed to {}: the far layer rebuilds",
                    IrisShaderPack.movedIds());
            restartRenderer();
        }
    }

    public static void submitChunk(LevelChunk chunk, IngestTrigger trigger) {
        IngestService ingest = ingestFor(chunk.getLevel());
        if (ingest != null) {
            ingest.submitChunk(chunk, trigger);
        }

        BeamSet beams = beamsFor(chunk.getLevel());
        if (beams != null && (trigger == IngestTrigger.PACKET || trigger == IngestTrigger.HELD)) {
            BeamTriggers.chunkArrived(beams, chunk);
        }
    }

    public static void blockChanged(ClientLevel source, BlockPos pos) {
        IngestService ingest = ingestFor(source);
        if (ingest != null) {
            ingest.markBlockChange(pos, System.currentTimeMillis());
        }

        BeamSet beams = beamsFor(source);
        if (beams != null) {
            BeamTriggers.blockChanged(beams, source, pos);
        }
    }

    public static void beaconTicked(Level source, BlockPos pos, BeaconBlockEntity beacon) {
        BeamSet beams = beamsFor(source);
        if (beams != null) {
            BeamTriggers.ticked(beams, source, pos, beacon);
        }
    }

    public static void lightUpdated(ClientLevel source, SectionPos pos) {
        IngestService ingest = ingestFor(source);
        if (ingest != null) {
            ingest.markLightUpdate(pos, System.currentTimeMillis());
        }
    }

    public static void lightPacketApplied(ClientLevel source, int chunkX, int chunkZ, BitSet sections) {
        IngestService ingest = ingestFor(source);
        if (ingest != null) {
            ingest.markLightPacket(chunkX, chunkZ, sections, source.getLightEngine().getMinLightSection(),
                    System.currentTimeMillis());
        }
    }

    @SuppressWarnings("ReferenceEquality")
    private static @Nullable IngestService ingestFor(Level source) {
        if (runtime == null || source != level) {
            return null;
        }

        return SettingsService.get().settings().ingestion() ? runtime.ingest() : null;
    }

    @SuppressWarnings("ReferenceEquality")
    private static @Nullable BeamSet beamsFor(Level source) {
        if (runtime == null || source != level) {
            return null;
        }

        return SettingsService.get().settings().ingestion() ? runtime.beams() : null;
    }

    private static void swapLevel(@Nullable ClientLevel current) {
        level = current;
        if (current == null) {
            stopRenderer();
            releaseRuntime();
            heldChunksPending = false;
            return;
        }

        Gpu next = Gpus.create();
        long replacedArenaBytes = replacedArenaBytes();
        stopRenderer();
        releaseRuntime();
        runtime = Objects.requireNonNull(instance, IN_SESSION).acquire(identityOf(current), current.getMinY());
        startRenderer(next, replacedArenaBytes);
        heldChunksPending = true;
    }

    private static void releaseRuntime() {
        if (runtime != null) {
            Objects.requireNonNull(instance, IN_SESSION).release(runtime);
            runtime = null;
        }
    }

    private static void submitHeldChunks() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel shown = level;
        if (shown == null) {
            return;
        }

        IngestService ingest = ingestFor(shown);
        if (ingest == null || minecraft.player == null) {
            return;
        }

        heldChunksPending = false;
        ChunkPos center = minecraft.player.chunkPosition();
        int radius = minecraft.options.getEffectiveRenderDistance() + CLIENT_EXTRA_CHUNKS;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = center.x() + dx;
                int chunkZ = center.z() + dz;
                LevelChunk chunk = shown.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk != null) {
                    ingest.submitChunk(chunk, IngestTrigger.HELD);
                }
            }
        }
    }

    private static void restartSession() {
        endSession();
        login();
    }

    private static void restartRenderer() {
        Gpu next = Gpus.create();
        long replacedArenaBytes = replacedArenaBytes();
        stopRenderer();
        startRenderer(next, replacedArenaBytes);
    }

    private static long replacedArenaBytes() {
        return renderer == null ? 0L : renderer.arenaBytes();
    }

    private static void startRenderer(Gpu gpu, long replacedArenaBytes) {
        Minecraft minecraft = Minecraft.getInstance();
        Settings settings = captureRenderedInputs(minecraft);
        FarStart start = FarRenderer.start(minecraft, gpu, Objects.requireNonNull(instance, IN_SESSION),
                Objects.requireNonNull(runtime, WITH_LEVEL), Objects.requireNonNull(level, WITH_LEVEL).getHeight(),
                settings, replacedArenaBytes, renderedPackBlocks);
        renderer = start.renderer();
        refusal = start.refusal();
    }

    private static Settings captureRenderedInputs(Minecraft minecraft) {
        Settings settings = SettingsService.get().settings();
        rendered = settings;
        renderedBiomeBlend = minecraft.options.biomeBlendRadius().get();
        renderedPackIds = IrisShaderPack.packIds(renderedPackIds);
        renderedPackBlocks = renderedPackIds == null ? PackBlocks.NONE
                : new PackBlocks(renderedPackIds, IrisShaderPack.movedIds());
        return settings;
    }

    private static void stopRenderer() {
        refusal = null;
        if (renderer != null) {
            IrisShaderPack.rendererStopped();
            renderer.close();
            renderer = null;
        }
    }

    private static WorldIdentity identityOf(ClientLevel current) {
        long seed = ((BiomeManagerAccessor) current.getBiomeManager()).eminus$biomeZoomSeed();
        return new WorldIdentity(world, seed, current.dimension().identifier().toString());
    }

    private static Path storeBase(Minecraft minecraft) {
        if (minecraft.hasSingleplayerServer()) {
            return StoreFolders.singleplayerBase(Objects.requireNonNull(minecraft.getSingleplayerServer(), TESTED)
                    .getWorldPath(LevelResource.ROOT));
        }

        return StoreFolders.multiplayerBase(minecraft.gameDirectory.toPath(), serverAddress(minecraft));
    }

    private static String worldName(Minecraft minecraft) {
        if (minecraft.hasSingleplayerServer()) {
            return Objects.requireNonNull(minecraft.getSingleplayerServer(), TESTED).getWorldData().getLevelName();
        }

        return serverAddress(minecraft);
    }

    private static String serverAddress(Minecraft minecraft) {
        ServerData server = minecraft.getCurrentServer();
        return server == null ? "" : server.ip;
    }

    private ClientSession() {
    }
}
