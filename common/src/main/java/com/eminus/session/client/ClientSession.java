package com.eminus.session.client;

import java.nio.file.Path;

import com.eminus.handoff.NearFieldOverride;
import com.eminus.handoff.client.VanillaVisibleSections;
import com.eminus.ingest.IngestService;
import com.eminus.mixin.BiomeManagerAccessor;
import com.eminus.render.far.client.FarRenderer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

public final class ClientSession {
    private static EminusInstance instance;
    private static DimensionRuntime runtime;
    private static FarRenderer renderer;
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
            return;
        }

        GameRenderState state = Minecraft.getInstance().gameRenderer.gameRenderState();
        FogData fog = state.levelRenderState.cameraRenderState.fogData;
        if (renderer.covers(fog, state.optionsRenderState.renderDistance)) {
            NearFieldOverride.apply(fog, state.optionsRenderState);
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

        ClientLevel current = Minecraft.getInstance().level;
        if (current != level) {
            swapLevel(current);
        }

        IngestService ingest = ingestFor(level);
        if (ingest != null) {
            ingest.pollDebounce(level, System.currentTimeMillis());
        }
    }

    public static void submitChunk(LevelChunk chunk) {
        IngestService ingest = ingestFor(chunk.getLevel());
        if (ingest != null) {
            ingest.submitChunk(chunk);
        }
    }

    public static void blockChanged(ClientLevel source, BlockPos pos) {
        IngestService ingest = ingestFor(source);
        if (ingest != null) {
            ingest.markBlockChange(pos, System.currentTimeMillis());
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
            Minecraft minecraft = Minecraft.getInstance();
            runtime = instance.acquire(identityOf(current), current.getMinY());
            renderer = FarRenderer.start(minecraft, instance, runtime,
                    new VanillaVisibleSections(minecraft.levelRenderer), current.getHeight());
        }
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
