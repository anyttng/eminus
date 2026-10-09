package com.eminus.ingest;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.VoxelEntry;

public final class PyramidDownsampler {
    private static final int NONE_FOUND = -1;
    private static final int CLEAR = 0;
    private static final int BLOCK_SUM_SHIFT = 16;
    private static final int COUNT_SHIFT = 32;
    private static final long SUM_MASK = 0xFFFFL;
    private static final long NO_COVER = VoxelEntry.AIR;

    public static void build(SectionPyramid pyramid, StateOpacity opacity) {
        for (int level = DetailLevel.MIN + 1; level <= DetailLevel.MAX; level++) {
            reduce(pyramid.level(level - 1), pyramid.level(level), SectionPyramid.sideOf(level),
                    DetailLevel.blocksPerVoxel(level - 1), opacity);
        }
    }

    private static void reduce(long[] source, long[] target, int targetSide, int sourceBlocks, StateOpacity opacity) {
        int sourceSide = targetSide * 2;

        for (int y = 0; y < targetSide; y++) {
            for (int z = 0; z < targetSide; z++) {
                for (int x = 0; x < targetSide; x++) {
                    target[SectionPyramid.indexAt(targetSide, x, y, z)] =
                            combine(source, sourceSide, sourceBlocks, x * 2, y * 2, z * 2, opacity);
                }
            }
        }
    }

    private static long combine(long[] source, int side, int sourceBlocks, int originX, int originY, int originZ,
            StateOpacity opacity) {
        long top = source[SectionPyramid.indexAt(side, originX + 1, originY + 1, originZ + 1)];
        long best = top;
        int bestOpacity = NONE_FOUND;
        int bestLowest = 0;
        int bestHighest = 0;
        int lowest = 2 * sourceBlocks;
        int highest = 0;
        int denseLowest = 2 * sourceBlocks;
        int denseHighest = 0;
        boolean coverSeen = false;
        long airLight = 0L;
        long clearLight = 0L;
        long gappedDenseLight = 0L;
        long gappedClearLight = 0L;

        for (int dy = 1; dy >= 0; dy--) {
            for (int dz = 1; dz >= 0; dz--) {
                for (int dx = 1; dx >= 0; dx--) {
                    long entry = source[SectionPyramid.indexAt(side, originX + dx, originY + dy, originZ + dz)];
                    if (VoxelEntry.isAir(entry)) {
                        airLight += lightOf(entry);
                        continue;
                    }

                    int state = VoxelEntry.state(entry);
                    int value = opacity.opacity(state);
                    int from = spanStart(entry, dy, sourceBlocks);
                    int to = spanEnd(entry, dy, sourceBlocks);
                    boolean gapped = VoxelEntry.gaps(entry) != VoxelEntry.NO_GAPS;
                    boolean cover = value == CLEAR && opacity.cover(state);
                    lowest = Math.min(lowest, from);
                    highest = Math.max(highest, to);
                    if (value > CLEAR || (cover && opacity.coversGround(entry, sourceBlocks))) {
                        denseLowest = Math.min(denseLowest, from);
                        denseHighest = Math.max(denseHighest, cover ? to - 1 : to);
                        gappedDenseLight += gapped ? lightOf(entry) : 0L;
                    } else {
                        clearLight += lightOf(entry);
                        gappedClearLight += gapped ? lightOf(entry) : 0L;
                    }

                    coverSeen |= cover;
                    if (value > bestOpacity) {
                        bestOpacity = value;
                        best = entry;
                        bestLowest = from;
                        bestHighest = to;
                    }
                }
            }
        }

        if (coverSeen && denseHighest > 0) {
            long cover = coverOn(source, side, sourceBlocks, originX, originY, originZ, denseHighest, opacity);
            if (cover != NO_COVER) {
                return gapped(cover, denseLowest, 2 * sourceBlocks - denseHighest - 1,
                        airLight + clearLight + gappedDenseLight);
            }
        }

        if (bestOpacity > CLEAR) {
            return gapped(best, denseLowest, 2 * sourceBlocks - denseHighest,
                    airLight + clearLight + gappedDenseLight);
        }

        if (bestOpacity == CLEAR && opacity.cover(VoxelEntry.state(best))) {
            return gapped(best, bestLowest, 2 * sourceBlocks - bestHighest, airLight + gappedClearLight);
        }

        if (bestOpacity == CLEAR) {
            return gapped(best, lowest, 2 * sourceBlocks - highest, airLight + gappedClearLight);
        }

        return VoxelEntry.pack(VoxelEntry.AIR_STATE_ID, VoxelEntry.biome(top), averageLight(airLight));
    }

    private static long coverOn(long[] source, int side, int sourceBlocks, int originX, int originY, int originZ,
            int groundTop, StateOpacity opacity) {
        for (int dy = 1; dy >= 0; dy--) {
            for (int dz = 1; dz >= 0; dz--) {
                for (int dx = 1; dx >= 0; dx--) {
                    long entry = source[SectionPyramid.indexAt(side, originX + dx, originY + dy, originZ + dz)];
                    if (spanEnd(entry, dy, sourceBlocks) - 1 == groundTop && opacity.cover(VoxelEntry.state(entry))) {
                        return entry;
                    }
                }
            }
        }

        return NO_COVER;
    }

    private static int spanStart(long entry, int dy, int sourceBlocks) {
        return dy * sourceBlocks + VoxelEntry.lowGap(entry);
    }

    private static int spanEnd(long entry, int dy, int sourceBlocks) {
        return (dy + 1) * sourceBlocks - VoxelEntry.highGap(entry);
    }

    private static long gapped(long winner, int lowGap, int highGap, long emptyLight) {
        long entry = VoxelEntry.withGaps(winner, lowGap, highGap);
        if (VoxelEntry.gaps(entry) == VoxelEntry.NO_GAPS || lightCount(emptyLight) == 0) {
            return entry;
        }

        return VoxelEntry.withLight(entry, averageLight(emptyLight));
    }

    private static long lightOf(long entry) {
        return VoxelEntry.skyLight(entry)
                | (long) VoxelEntry.blockLight(entry) << BLOCK_SUM_SHIFT
                | 1L << COUNT_SHIFT;
    }

    private static int lightCount(long sums) {
        return (int) (sums >>> COUNT_SHIFT & SUM_MASK);
    }

    private static int averageLight(long sums) {
        int count = lightCount(sums);
        return VoxelEntry.light((int) (sums & SUM_MASK) / count, (int) (sums >>> BLOCK_SUM_SHIFT & SUM_MASK) / count);
    }

    private PyramidDownsampler() {
    }
}
