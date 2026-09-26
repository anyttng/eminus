package com.eminus.compat.sodium;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.SectionPos;

public final class SodiumDrawnSections {
    private static final LongOpenHashSet VISITED = new LongOpenHashSet();

    private static Reach pending = Reach.NONE;
    private static Reach published = Reach.NONE;

    public static void traversal(int cameraX, int cameraY, int cameraZ, float fracX, float fracY, float fracZ,
            float searchDistance) {
        pending = new Reach(cameraX, cameraY, cameraZ, fracX, fracY, fracZ, searchDistance);
    }

    public static void visited(int sectionX, int sectionY, int sectionZ, boolean built) {
        if (built) {
            VISITED.add(SectionPos.asLong(sectionX, sectionY, sectionZ));
        }
    }

    public static void removed(int sectionX, int sectionY, int sectionZ) {
        VISITED.remove(SectionPos.asLong(sectionX, sectionY, sectionZ));
    }

    public static void reset() {
        VISITED.clear();
        pending = Reach.NONE;
        published = Reach.NONE;
    }

    public static void publish() {
        published = pending;
    }

    public static boolean drawn(int sectionX, int sectionY, int sectionZ) {
        return VISITED.contains(SectionPos.asLong(sectionX, sectionY, sectionZ))
                && published.covers(sectionX, sectionY, sectionZ);
    }

    // Repeats OcclusionCuller.isWithinRenderDistance, which Sodium keeps private.
    record Reach(int cameraX, int cameraY, int cameraZ, float fracX, float fracY, float fracZ, float searchDistance) {
        static final Reach NONE = new Reach(0, 0, 0, 0.0F, 0.0F, 0.0F, 0.0F);

        private static final int SECTION_SHIFT = 4;
        private static final int SECTION_BLOCKS = 1 << SECTION_SHIFT;
        private static final int BOX_MARGIN = 1;

        boolean covers(int sectionX, int sectionY, int sectionZ) {
            float dx = nearestToZero((sectionX << SECTION_SHIFT) - cameraX) - fracX;
            float dy = nearestToZero((sectionY << SECTION_SHIFT) - cameraY) - fracY;
            float dz = nearestToZero((sectionZ << SECTION_SHIFT) - cameraZ) - fracZ;
            return dx * dx + dz * dz < searchDistance * searchDistance && Math.abs(dy) < searchDistance;
        }

        private static int nearestToZero(int offset) {
            int min = offset - BOX_MARGIN;
            int max = offset + SECTION_BLOCKS + BOX_MARGIN;
            if (min > 0) {
                return min;
            }

            return max < 0 ? max : 0;
        }
    }

    private SodiumDrawnSections() {
    }
}
