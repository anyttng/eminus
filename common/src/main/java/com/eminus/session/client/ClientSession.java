package com.eminus.session.client;

import java.nio.file.Path;

import com.eminus.ingest.IngestService;
import com.eminus.mixin.BiomeManagerAccessor;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

import org.jspecify.annotations.Nullable;

public final class ClientSession {
    private static EminusInstance instance;
    private static DimensionRuntime runtime;
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

        level = null;
        runtime = null;
        instance.shutdown();
        instance = null;
    }

    public static @Nullable EminusInstance instance() {
        return instance;
    }

    public static @Nullable DimensionRuntime runtime() {
        return runtime;
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

        if (runtime != null) {
            instance.release(runtime);
            runtime = null;
        }

        if (current != null) {
            runtime = instance.acquire(identityOf(current), current.getMinY());
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
