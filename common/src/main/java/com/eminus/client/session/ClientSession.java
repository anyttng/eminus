package com.eminus.client.session;

import java.nio.file.Path;
import java.util.BitSet;

import com.eminus.Eminus;
import com.eminus.handoff.NearFieldOverride;
import com.eminus.ingest.IngestService;
import com.eminus.ingest.IngestTrigger;
import com.eminus.mixin.BiomeManagerAccessor;
import com.eminus.client.render.far.FarRenderer;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;
import com.eminus.session.StoreFolders;
import com.eminus.session.WorldIdentity;
import com.eminus.settings.Settings;
import com.eminus.settings.SettingsService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.storage.LevelResource;

import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

public final class ClientSession {
    public static final Identifier RELOAD_ID = Identifier.fromNamespaceAndPath(Eminus.MODID, "far_renderer");

    public static final int CLIENT_EXTRA_CHUNKS = 3;

    private static boolean heldChunksPending;
    private static boolean renderedCutoutLeaves;
    private static int renderedBiomeBlend;
    private static EminusInstance instance;
    private static DimensionRuntime runtime;
    private static FarRenderer renderer;
    private static Settings rendered;
    private static ClientLevel level;
    private static String world = "";

    public static void login() {
        if (instance != null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        world = worldName(minecraft);
        Settings settings = SettingsService.get().settings();
        instance = EminusInstance.start(
                storeBase(minecraft),
                settings.workerThreads(),
                settings.lowestStoredLevel(),
                System::currentTimeMillis);
    }

    public static void disconnect() {
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
        if (renderer != null) {
            restartRenderer();
        }
    }

    public static void settingsChanged(Settings updated) {
        if (instance == null) {
            return;
        }

        if (updated.lowestStoredLevel() != instance.lowestStoredLevel()) {
            restartSession();
            return;
        }

        if (updated.workerThreads() != instance.workerThreads()) {
            instance.resizeWorkers(updated.workerThreads());
        }

        if (runtime != null && FarRenderer.recreates(rendered, updated)) {
            restartRenderer();
        }
    }

    public static void captureLevelProjection(Matrix4fc levelProjection, Matrix4fc cameraProjection) {
        if (renderer != null) {
            renderer.captureLevelProjection(levelProjection, cameraProjection);
        }
    }

    public static void drawFarLayer() {
        if (renderer != null) {
            renderer.frame(Minecraft.getInstance());
        }
    }

    public static void overrideNearField() {
        if (renderer == null) {
            NearFieldOverride.skip();
            return;
        }

        Minecraft client = Minecraft.getInstance();
        GameRenderState state = client.gameRenderer.gameRenderState();
        FogData fog = state.levelRenderState.cameraRenderState.fogData;
        if (renderer.covers(fog, state.optionsRenderState.renderDistance)) {
            boolean inAir = client.gameRenderer.mainCamera().getFluidInCamera() == FogType.NONE;
            NearFieldOverride.apply(fog, state.optionsRenderState, inAir && !SettingsService.get().settings().fog());
        } else {
            NearFieldOverride.skip();
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

    public static void tick() {
        if (instance == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel current = minecraft.level;
        if (current != level) {
            swapLevel(current);
        }

        if (renderer != null && (minecraft.options.cutoutLeaves().get() != renderedCutoutLeaves
                || minecraft.options.biomeBlendRadius().get() != renderedBiomeBlend)) {
            restartRenderer();
        }

        if (heldChunksPending) {
            submitHeldChunks();
        }

        IngestService ingest = ingestFor(level);
        if (ingest != null) {
            ingest.pollDebounce(level, System.currentTimeMillis());
        }
    }

    public static void submitChunk(LevelChunk chunk, IngestTrigger trigger) {
        IngestService ingest = ingestFor(chunk.getLevel());
        if (ingest != null) {
            ingest.submitChunk(chunk, trigger);
        }
    }

    public static void blockChanged(ClientLevel source, BlockPos pos) {
        IngestService ingest = ingestFor(source);
        if (ingest != null) {
            ingest.markBlockChange(pos, System.currentTimeMillis());
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

    private static IngestService ingestFor(Level source) {
        if (runtime == null || source != level) {
            return null;
        }

        return SettingsService.get().settings().ingestion() ? runtime.ingest() : null;
    }

    private static void swapLevel(ClientLevel current) {
        level = current;
        stopRenderer();

        if (runtime != null) {
            instance.release(runtime);
            runtime = null;
        }

        if (current != null) {
            runtime = instance.acquire(identityOf(current), current.getMinY());
            startRenderer();
        }

        heldChunksPending = current != null;
    }

    private static void submitHeldChunks() {
        Minecraft minecraft = Minecraft.getInstance();
        IngestService ingest = ingestFor(level);
        if (ingest == null || minecraft.player == null) {
            return;
        }

        heldChunksPending = false;
        ChunkPos centre = minecraft.player.chunkPosition();
        int radius = minecraft.options.getEffectiveRenderDistance() + CLIENT_EXTRA_CHUNKS;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = centre.x() + dx;
                int chunkZ = centre.z() + dz;
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk != null) {
                    ingest.submitChunk(chunk, IngestTrigger.HELD);
                }
            }
        }
    }

    private static void restartSession() {
        disconnect();
        login();
    }

    private static void restartRenderer() {
        stopRenderer();
        startRenderer();
    }

    private static void startRenderer() {
        Minecraft minecraft = Minecraft.getInstance();
        rendered = SettingsService.get().settings();
        renderedCutoutLeaves = minecraft.options.cutoutLeaves().get();
        renderedBiomeBlend = minecraft.options.biomeBlendRadius().get();
        renderer = FarRenderer.start(minecraft, instance, runtime, level.getHeight(), rendered);
    }

    private static void stopRenderer() {
        if (renderer != null) {
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
            return StoreFolders.singleplayerBase(minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT));
        }

        return StoreFolders.multiplayerBase(minecraft.gameDirectory.toPath(), serverAddress(minecraft));
    }

    private static String worldName(Minecraft minecraft) {
        if (minecraft.hasSingleplayerServer()) {
            return minecraft.getSingleplayerServer().getWorldData().getLevelName();
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
