package com.eminus.handoff;

import java.util.Arrays;

public final class NearSections {
    public static final int SECTION_BLOCKS = 16;
    public static final int BITS_PER_TEXEL = Integer.SIZE;
    public static final int TEXEL_SHIFT = Integer.numberOfTrailingZeros(BITS_PER_TEXEL);
    public static final int OUTSIDE = -1;

    private static final int BIT_MASK = BITS_PER_TEXEL - 1;
    private static final int SECTION_SHIFT = Integer.numberOfTrailingZeros(SECTION_BLOCKS);
    private static final int VIEW_DISTANCE_SLACK_CHUNKS = 1;

    private int originX;
    private int originY;
    private int originZ;
    private int side;
    private int height;
    private int texels;
    private int queried;
    private int built;
    private int[] owned = new int[0];
    private int[] asked = new int[0];

    public static int section(int block) {
        return block >> SECTION_SHIFT;
    }

    // Repeats ChunkTrackingView.isInViewDistance and SectionOcclusionGraph's vertical cut, which vanilla keeps out of reach.
    public static boolean inVanillaViewDistance(int cameraSectionX, int cameraSectionY, int cameraSectionZ,
            int viewDistance, int sectionX, int sectionY, int sectionZ) {
        long dx = Math.max(0, Math.abs(sectionX - cameraSectionX) - VIEW_DISTANCE_SLACK_CHUNKS);
        long dz = Math.max(0, Math.abs(sectionZ - cameraSectionZ) - VIEW_DISTANCE_SLACK_CHUNKS);
        return dx * dx + dz * dz < (long) viewDistance * viewDistance
                && Math.abs(sectionY - cameraSectionY) <= viewDistance;
    }

    public void reset(int centreSectionX, int centreSectionZ, int radius, int minSectionY, int sectionCount) {
        side = 2 * radius + 1;
        height = sectionCount;
        originX = centreSectionX - radius;
        originY = minSectionY;
        originZ = centreSectionZ - radius;
        texels = Math.ceilDiv(side * side * height, BITS_PER_TEXEL);
        queried = 0;
        built = 0;

        if (owned.length < texels) {
            owned = new int[texels];
            asked = new int[texels];
        } else {
            Arrays.fill(owned, 0, texels, 0);
            Arrays.fill(asked, 0, texels, 0);
        }
    }

    public int index(int sectionX, int sectionY, int sectionZ) {
        int dx = sectionX - originX;
        int dy = sectionY - originY;
        int dz = sectionZ - originZ;
        if (dx < 0 || dx >= side || dy < 0 || dy >= height || dz < 0 || dz >= side) {
            return OUTSIDE;
        }

        return (dz * side + dx) * height + dy;
    }

    public boolean contains(int sectionX, int sectionY, int sectionZ) {
        return index(sectionX, sectionY, sectionZ) != OUTSIDE;
    }

    public boolean owned(int sectionX, int sectionY, int sectionZ) {
        int index = index(sectionX, sectionY, sectionZ);
        return index != OUTSIDE && (owned[index >>> TEXEL_SHIFT] & bit(index)) != 0;
    }

    public void queryBlocks(int minBlockX, int minBlockY, int minBlockZ, int endBlockX, int endBlockY, int endBlockZ,
            SectionQuery query) {
        int fromX = Math.max(section(minBlockX), originX);
        int fromY = Math.max(section(minBlockY), originY);
        int fromZ = Math.max(section(minBlockZ), originZ);
        int toX = Math.min(section(endBlockX - 1), originX + side - 1);
        int toY = Math.min(section(endBlockY - 1), originY + height - 1);
        int toZ = Math.min(section(endBlockZ - 1), originZ + side - 1);

        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                for (int y = fromY; y <= toY; y++) {
                    ask(x, y, z, query);
                }
            }
        }
    }

    public int originBlockX() {
        return originX * SECTION_BLOCKS;
    }

    public int originBlockY() {
        return originY * SECTION_BLOCKS;
    }

    public int originBlockZ() {
        return originZ * SECTION_BLOCKS;
    }

    public int side() {
        return side;
    }

    public int height() {
        return height;
    }

    public int texels() {
        return texels;
    }

    public int queried() {
        return queried;
    }

    public int built() {
        return built;
    }

    public int[] owned() {
        return owned;
    }

    private void ask(int x, int y, int z, SectionQuery query) {
        int index = index(x, y, z);
        int texel = index >>> TEXEL_SHIFT;
        int bit = bit(index);
        if ((asked[texel] & bit) != 0) {
            return;
        }

        asked[texel] |= bit;
        queried++;
        if (query.built(x, y, z)) {
            owned[texel] |= bit;
            built++;
        }
    }

    private static int bit(int index) {
        return 1 << (index & BIT_MASK);
    }

    @FunctionalInterface
    public interface SectionQuery {
        boolean built(int sectionX, int sectionY, int sectionZ);
    }
}
