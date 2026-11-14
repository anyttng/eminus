package com.eminus.ingest;

import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;

import org.jspecify.annotations.Nullable;

public interface IngestColumn {
    int chunkX();

    int chunkZ();

    int sectionCount();

    int sectionY(int index);

    boolean submits(int index);

    LevelChunkSection section(int index);

    BiomeWindow biomeWindow(int index);

    DataLayer skyLight(int index);

    @Nullable DataLayer blockLight(int index);
}
