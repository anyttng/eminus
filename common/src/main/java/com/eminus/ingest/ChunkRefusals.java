package com.eminus.ingest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.world.level.Level;

// mc/1.21: compat/sable registers the plot grid of Sable sub-levels, whose chunks are not terrain where they sit.
public final class ChunkRefusals {
    private static final List<Refusal> REFUSALS = new CopyOnWriteArrayList<>();

    public static void register(Refusal refusal) {
        REFUSALS.add(refusal);
    }

    public static boolean refuses(Level level, int chunkX, int chunkZ) {
        for (Refusal refusal : REFUSALS) {
            if (refusal.refuses(level, chunkX, chunkZ)) {
                return true;
            }
        }

        return false;
    }

    @FunctionalInterface
    public interface Refusal {
        boolean refuses(Level level, int chunkX, int chunkZ);
    }

    private ChunkRefusals() {
    }
}
