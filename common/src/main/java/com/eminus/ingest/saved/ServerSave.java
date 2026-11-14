package com.eminus.ingest.saved;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.eminus.Eminus;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import org.jspecify.annotations.Nullable;

public final class ServerSave implements SaveSource {
    private static final String REGION_FOLDER = "region";
    private static final int NO_DATA_VERSION = -1;

    private final ServerLevel level;
    private final ChunkMap storage;
    private final Path regionFolder;

    public ServerSave(ServerLevel level) {
        this.level = level;
        this.storage = level.getChunkSource().chunkMap;
        this.regionFolder = DimensionType
                .getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT))
                .resolve(REGION_FOLDER);
    }

    @Override
    public LongSet regions() {
        LongSet regions = new LongOpenHashSet();
        if (!Files.isDirectory(regionFolder)) {
            return regions;
        }

        try (Stream<Path> files = Files.list(regionFolder)) {
            files.forEach(file -> ImportTiles.regionOf(file.getFileName().toString()).ifPresent(regions::add));
        } catch (IOException unreadable) {
            Eminus.LOGGER.warn("The save's region folder {} could not be listed; nothing is imported.",
                    regionFolder, unreadable);
        }

        return regions;
    }

    @Override
    public CompletableFuture<Optional<CompoundTag>> read(int chunkX, int chunkZ) {
        return storage.read(new ChunkPos(chunkX, chunkZ));
    }

    @Override
    public @Nullable SavedChunk parse(CompoundTag chunkTag) {
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        boolean older = NbtUtils.getDataVersion(chunkTag, NO_DATA_VERSION) < current;
        CompoundTag upgraded = older
                ? storage.upgradeChunkTag(chunkTag, NO_DATA_VERSION, ChunkMap.getChunkDataFixContextTag(
                        level.dimension(), level.getChunkSource().getGenerator().getTypeNameForDataFixer()), current)
                : chunkTag;
        SerializableChunkData data = SerializableChunkData.parse(level, level.palettedContainerFactory(), upgraded);
        return data == null ? null : SavedChunk.of(data, level.getSectionsCount(), older);
    }

    @Override
    public boolean hasSky() {
        return level.dimensionType().hasSkyLight();
    }
}
