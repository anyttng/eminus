package com.eminus.mesh;

import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

public final class QuadLight {
    public static int of(long entry, int metadata) {
        int emission = ModelMetadata.emission(metadata);
        return VoxelEntry.light(VoxelEntry.skyLight(entry), Math.max(VoxelEntry.blockLight(entry), emission));
    }

    public static int ofFluid(long entry, long other, int metadata) {
        int emission = ModelMetadata.emission(metadata);
        int blockLight = Math.max(VoxelEntry.blockLight(entry), VoxelEntry.blockLight(other));
        return VoxelEntry.light(Math.max(VoxelEntry.skyLight(entry), VoxelEntry.skyLight(other)),
                Math.max(blockLight, emission));
    }

    private QuadLight() {
    }
}
