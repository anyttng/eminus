package com.eminus.ingest;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.VoxelEntry;

public final class PyramidDownsampler {
    private static final int GROUP_VOXELS = 8;
    private static final int NONE_FOUND = -1;

    public static void build(SectionPyramid pyramid, StateOpacity opacity) {
        for (int level = DetailLevel.MIN + 1; level <= DetailLevel.MAX; level++) {
            reduce(pyramid.level(level - 1), pyramid.level(level), SectionPyramid.sideOf(level), opacity);
        }
    }

    private static void reduce(long[] source, long[] target, int targetSide, StateOpacity opacity) {
        int sourceSide = targetSide * 2;

        for (int y = 0; y < targetSide; y++) {
            for (int z = 0; z < targetSide; z++) {
                for (int x = 0; x < targetSide; x++) {
                    target[SectionPyramid.indexAt(targetSide, x, y, z)] =
                            combine(source, sourceSide, x * 2, y * 2, z * 2, opacity);
                }
            }
        }
    }

    private static long combine(long[] source, int side, int originX, int originY, int originZ, StateOpacity opacity) {
        long top = source[SectionPyramid.indexAt(side, originX + 1, originY + 1, originZ + 1)];
        long best = top;
        int bestOpacity = NONE_FOUND;
        int skySum = 0;
        int blockSum = 0;

        for (int dy = 1; dy >= 0; dy--) {
            for (int dz = 1; dz >= 0; dz--) {
                for (int dx = 1; dx >= 0; dx--) {
                    long entry = source[SectionPyramid.indexAt(side, originX + dx, originY + dy, originZ + dz)];
                    skySum += VoxelEntry.skyLight(entry);
                    blockSum += VoxelEntry.blockLight(entry);

                    if (VoxelEntry.isAir(entry)) {
                        continue;
                    }

                    int value = opacity.opacity(VoxelEntry.state(entry));
                    if (value > bestOpacity) {
                        bestOpacity = value;
                        best = entry;
                    }
                }
            }
        }

        if (bestOpacity != NONE_FOUND) {
            return best;
        }

        return VoxelEntry.pack(VoxelEntry.AIR_STATE_ID, VoxelEntry.biome(top),
                VoxelEntry.light(skySum / GROUP_VOXELS, blockSum / GROUP_VOXELS));
    }

    private PyramidDownsampler() {
    }
}
