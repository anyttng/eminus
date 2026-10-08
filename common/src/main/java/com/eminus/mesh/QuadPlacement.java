package com.eminus.mesh;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;

public final class QuadPlacement {
    public static final int NONE = 0;

    public static boolean gapped(int level) {
        return level != DetailLevel.MIN;
    }

    public static int ofBlock(int level, long entry, int offset) {
        return gapped(level) ? VoxelEntry.gaps(entry) : offset;
    }

    public static int ofFluid(int level, long entry, int corners) {
        return gapped(level) ? VoxelEntry.gaps(entry) : corners;
    }

    public static int gaps(int level, int placement) {
        return gapped(level) ? placement : VoxelEntry.NO_GAPS;
    }

    public static int lowGap(int level, int placement) {
        return VoxelEntry.lowGapOf(gaps(level, placement));
    }

    public static int highGap(int level, int placement) {
        return VoxelEntry.highGapOf(gaps(level, placement));
    }

    public static int offset(int level, boolean fluid, int placement) {
        return gapped(level) || fluid ? QuadOffset.NONE : placement;
    }

    public static int corners(int level, boolean fluid, int placement) {
        return gapped(level) || !fluid ? FluidCorners.FLAT : placement;
    }

    private QuadPlacement() {
    }
}
