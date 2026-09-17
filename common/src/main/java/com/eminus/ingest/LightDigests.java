package com.eminus.ingest;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;

import org.jspecify.annotations.Nullable;

public final class LightDigests {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final long ABSENT = 0L;
    private static final int NIBBLE_VALUES = 16;

    private final Long2LongOpenHashMap sky = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap block = new Long2LongOpenHashMap();

    public LightDigests() {
        sky.defaultReturnValue(ABSENT);
        block.defaultReturnValue(ABSENT);
    }

    public synchronized boolean record(long sectionNode, @Nullable DataLayer skyLight,
            @Nullable DataLayer blockLight) {
        long skyDigest = digest(skyLight);
        long blockDigest = digest(blockLight);
        boolean differs = sky.put(sectionNode, skyDigest) != skyDigest
                | block.put(sectionNode, blockDigest) != blockDigest;
        return differs;
    }

    public synchronized void forgetColumn(int chunkX, int chunkZ, int minSectionY, int maxSectionY) {
        for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
            long sectionNode = SectionPos.asLong(chunkX, sectionY, chunkZ);
            sky.remove(sectionNode);
            block.remove(sectionNode);
        }
    }

    public synchronized void clear() {
        sky.clear();
        sky.trim();
        block.clear();
        block.trim();
    }

    private static long digest(@Nullable DataLayer layer) {
        if (layer == null) {
            return ABSENT;
        }

        if (layer.isDefinitelyHomogenous()) {
            for (int value = 0; value < NIBBLE_VALUES; value++) {
                if (layer.isDefinitelyFilledWith(value)) {
                    return FNV_OFFSET ^ (value + 1L);
                }
            }

            return FNV_OFFSET;
        }

        long hash = FNV_OFFSET;
        for (byte nibbles : layer.getData()) {
            hash = (hash ^ nibbles) * FNV_PRIME;
        }

        return hash;
    }
}
