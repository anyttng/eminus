package com.eminus.mesh;

import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

public final class QuadLight {
    public static final QuadLight UNDER_SKY = new QuadLight(true);
    public static final QuadLight WITHOUT_SKY = new QuadLight(false);

    private static final int NO_SKY_LIGHT = 0;

    private final boolean hasSkyLight;

    private QuadLight(boolean hasSkyLight) {
        this.hasSkyLight = hasSkyLight;
    }

    public static QuadLight in(boolean hasSkyLight) {
        return hasSkyLight ? UNDER_SKY : WITHOUT_SKY;
    }

    public int of(long entry, int metadata) {
        int emission = ModelMetadata.emission(metadata);
        return VoxelEntry.light(sky(VoxelEntry.skyLight(entry)), Math.max(VoxelEntry.blockLight(entry), emission));
    }

    public int ofFluid(long entry, long other, int metadata) {
        int emission = ModelMetadata.emission(metadata);
        int blockLight = Math.max(VoxelEntry.blockLight(entry), VoxelEntry.blockLight(other));
        return VoxelEntry.light(sky(Math.max(VoxelEntry.skyLight(entry), VoxelEntry.skyLight(other))),
                Math.max(blockLight, emission));
    }

    private int sky(int stored) {
        return hasSkyLight ? stored : NO_SKY_LIGHT;
    }
}
