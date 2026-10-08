package com.eminus.compat.sodium;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import com.eminus.compat.iris.IrisShaderPack;

import net.minecraft.core.SectionPos;

public final class SodiumDrawnSections {
    private static final LongOpenHashSet VISITED = new LongOpenHashSet();
    private static final LongOpenHashSet DRAWN = new LongOpenHashSet();

    private static boolean recording;

    public static void beginTraversal() {
        recording = !IrisShaderPack.renderingShadowPass();
        if (recording) {
            VISITED.clear();
        }
    }

    public static void visited(int sectionX, int sectionY, int sectionZ) {
        if (recording) {
            VISITED.add(SectionPos.asLong(sectionX, sectionY, sectionZ));
        }
    }

    public static void publish() {
        if (IrisShaderPack.renderingShadowPass()) {
            return;
        }

        DRAWN.clear();
        DRAWN.addAll(VISITED);
    }

    public static boolean drawn(int sectionX, int sectionY, int sectionZ) {
        return DRAWN.contains(SectionPos.asLong(sectionX, sectionY, sectionZ));
    }

    private SodiumDrawnSections() {
    }
}
