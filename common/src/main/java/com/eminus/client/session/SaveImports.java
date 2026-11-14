package com.eminus.client.session;

import com.eminus.ingest.saved.SaveImport;
import com.eminus.ingest.saved.ServerSave;
import com.eminus.mixin.BiomeManagerAccessor;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;
import com.eminus.settings.FarDistance;
import com.eminus.settings.Settings;
import com.eminus.settings.SettingsService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import org.jspecify.annotations.Nullable;

public final class SaveImports {
    public static final String OFF_PROPERTY = "eminus.import.off";

    private static @Nullable SaveImport current;
    private static @Nullable DimensionRuntime importedInto;

    public static @Nullable SaveImport current() {
        return current;
    }

    @SuppressWarnings("ReferenceEquality")
    static void tick(Minecraft minecraft, EminusInstance instance, @Nullable DimensionRuntime runtime,
            @Nullable ClientLevel level) {
        if (runtime != importedInto) {
            stop();
            importedInto = runtime;
            current = runtime == null || level == null ? null : open(minecraft, instance, runtime, level);
        }

        SaveImport running = current;
        LocalPlayer player = minecraft.player;
        Settings settings = SettingsService.get().settings();
        if (running != null && player != null && settings.ingestion()) {
            ChunkPos at = player.chunkPosition();
            running.tick(at.x(), at.z(), FarDistance.cellsToChunks(settings.farRenderCells()),
                    minecraft.options.getEffectiveRenderDistance() + ClientSession.CLIENT_EXTRA_CHUNKS);
        }
    }

    static void stop() {
        if (current != null) {
            current.stop();
        }

        current = null;
        importedInto = null;
    }

    private static @Nullable SaveImport open(Minecraft minecraft, EminusInstance instance, DimensionRuntime runtime,
            ClientLevel level) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        ServerLevel saved = server == null ? null : server.getLevel(level.dimension());
        if (saved == null || Boolean.getBoolean(OFF_PROPERTY)) {
            return null;
        }

        long seed = ((BiomeManagerAccessor) level.getBiomeManager()).eminus$biomeZoomSeed();
        return new SaveImport(new ServerSave(saved), runtime.ingest(), runtime.coverage(), instance.imports(), seed);
    }

    private SaveImports() {
    }
}
