package com.eminus.compat.sodium;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.SectionPos;

public final class SodiumDrawnSections {
    private static final LongOpenHashSet VISITED = new LongOpenHashSet();
    private static final LongOpenHashSet DRAWN = new LongOpenHashSet();

    public static void beginTraversal() {
        VISITED.clear();
    }

    public static void visited(int sectionX, int sectionY, int sectionZ) {
        VISITED.add(SectionPos.asLong(sectionX, sectionY, sectionZ));
    }

    public static void publish() {
        DRAWN.clear();
        DRAWN.addAll(VISITED);
    }

    public static boolean drawn(int sectionX, int sectionY, int sectionZ) {
        return DRAWN.contains(SectionPos.asLong(sectionX, sectionY, sectionZ));
    }

    private SodiumDrawnSections() {
    }
}
