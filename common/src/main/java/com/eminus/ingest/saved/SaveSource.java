package com.eminus.ingest.saved;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.nbt.CompoundTag;

import org.jspecify.annotations.Nullable;

public interface SaveSource {
    LongSet regions();

    CompletableFuture<Optional<CompoundTag>> read(int chunkX, int chunkZ);

    @Nullable SavedChunk parse(CompoundTag chunkTag);

    boolean hasSky();
}
