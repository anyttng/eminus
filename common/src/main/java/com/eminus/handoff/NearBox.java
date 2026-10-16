package com.eminus.handoff;

public record NearBox(int minBlockX, int minBlockZ, int endBlockX, int endBlockZ) {
    public static final int MARGIN_BLOCKS = 1;

    public static NearBox around(int centreSectionX, int centreSectionZ, int radius) {
        return new NearBox((centreSectionX - radius) * NearSections.SECTION_BLOCKS,
                (centreSectionZ - radius) * NearSections.SECTION_BLOCKS,
                (centreSectionX + radius + 1) * NearSections.SECTION_BLOCKS,
                (centreSectionZ + radius + 1) * NearSections.SECTION_BLOCKS);
    }

    public boolean meets(int minX, int minZ, int side) {
        return minX - MARGIN_BLOCKS < endBlockX && minX + side + MARGIN_BLOCKS > minBlockX
                && minZ - MARGIN_BLOCKS < endBlockZ && minZ + side + MARGIN_BLOCKS > minBlockZ;
    }
}
