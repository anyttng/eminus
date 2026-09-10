package com.eminus.mesh;

import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

public final class QuadLight {
    public static final int QUANTIZED_FROM_LEVEL = 2;
    public static final int COARSE_SKY_BITS = 2;

    private static final int LIGHT_BITS = 4;
    private static final int SKY_DROPPED_BITS = LIGHT_BITS - COARSE_SKY_BITS;

    public static int of(long entry, int metadata, int level) {
        int emission = ModelMetadata.emission(metadata);

        if (level >= QUANTIZED_FROM_LEVEL) {
            return VoxelEntry.light(VoxelEntry.skyLight(entry) >>> SKY_DROPPED_BITS << SKY_DROPPED_BITS, emission);
        }

        return VoxelEntry.light(VoxelEntry.skyLight(entry), Math.max(VoxelEntry.blockLight(entry), emission));
    }

    private QuadLight() {
    }
}
