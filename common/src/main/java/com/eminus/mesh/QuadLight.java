package com.eminus.mesh;

import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

public final class QuadLight {
    public static int of(long entry, int metadata) {
        int emission = ModelMetadata.emission(metadata);
        return VoxelEntry.light(VoxelEntry.skyLight(entry), Math.max(VoxelEntry.blockLight(entry), emission));
    }

    private QuadLight() {
    }
}
