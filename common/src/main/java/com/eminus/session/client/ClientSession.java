package com.eminus.session.client;

import java.nio.file.Path;

import com.eminus.mixin.BiomeManagerAccessor;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;
import com.eminus.session.StoreFolders;
import com.eminus.session.WorldIdentity;
import com.eminus.settings.SettingsService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

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
        instance = EminusInstance.start(
                storeBase(minecraft),
                SettingsService.get().settings().workerThreads(),
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

    public static void tick() {
        if (instance == null) {
            return;
        }

        ClientLevel current = Minecraft.getInstance().level;
        if (current == level) {
            return;
        }

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
