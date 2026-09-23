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
        int lowest = 2 * sourceBlocks;
        int highest = 0;
        int denseLowest = 2 * sourceBlocks;
        int denseHighest = 0;
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

                    int value = opacity.opacity(VoxelEntry.state(entry));
                    int from = dy * sourceBlocks + VoxelEntry.lowGap(entry);
                    int to = (dy + 1) * sourceBlocks - VoxelEntry.highGap(entry);
                    boolean gapped = VoxelEntry.gaps(entry) != VoxelEntry.NO_GAPS;
                    lowest = Math.min(lowest, from);
                    highest = Math.max(highest, to);
                    if (value > CLEAR) {
                        denseLowest = Math.min(denseLowest, from);
                        denseHighest = Math.max(denseHighest, to);
                        gappedDenseLight += gapped ? lightOf(entry) : 0L;
                    } else {
                        clearLight += lightOf(entry);
                        gappedClearLight += gapped ? lightOf(entry) : 0L;
                    }

                    if (value > bestOpacity) {
                        bestOpacity = value;
                        best = entry;
                    }
                }
            }
        }

        if (bestOpacity > CLEAR) {
            return gapped(best, denseLowest, 2 * sourceBlocks - denseHighest,
                    airLight + clearLight + gappedDenseLight);
        }

        if (bestOpacity == CLEAR) {
            return gapped(best, lowest, 2 * sourceBlocks - highest, airLight + gappedClearLight);
        }

        return VoxelEntry.pack(VoxelEntry.AIR_STATE_ID, VoxelEntry.biome(top), averageLight(airLight));
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
