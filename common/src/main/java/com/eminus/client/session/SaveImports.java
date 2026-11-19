package com.eminus.client.session;

import com.eminus.ingest.saved.SaveImport;
import com.eminus.ingest.saved.SavedChunk;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import org.jspecify.annotations.Nullable;

public final class SaveImports {
    public static final String OFF_PROPERTY = "eminus.import.off";

    private static final int MIN_VIEW_CHUNKS = 2;
    private static final int MAX_VIEW_CHUNKS = 32;

    private static volatile @Nullable SaveImport current;
    private static volatile @Nullable ServerLevel savedLevel;
    private static @Nullable DimensionRuntime importedInto;

    public static @Nullable SaveImport current() {
        return current;
    }

    @SuppressWarnings("ReferenceEquality")
    public static void chunkSaved(ServerLevel level, CompoundTag snapshot) {
        SaveImport running = current;
        if (running == null || level != savedLevel) {
            return;
        }

        SavedChunk chunk = SavedChunk.of(level, snapshot, false);
        if (chunk != null) {
            running.saved(chunk);
        }
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
            running.tick(at.x, at.z, FarDistance.cellsToChunks(settings.farRenderCells()),
                    Mth.clamp(minecraft.options.getEffectiveRenderDistance(), MIN_VIEW_CHUNKS, MAX_VIEW_CHUNKS));
        }
    }

    static void stop() {
        SaveImport running = current;
        if (running != null) {
            running.stop();
        }

        current = null;
        savedLevel = null;
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
        savedLevel = saved;
        return new SaveImport(new ServerSave(saved), runtime.ingest(), runtime.coverage(), instance.imports(), seed);
    }

    private SaveImports() {
    }
}
