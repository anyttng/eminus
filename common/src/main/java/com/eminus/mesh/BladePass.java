package com.eminus.mesh;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

public final class BladePass {
    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int ONE_VOXEL = 1;

    private final MeshScratch scratch;
    private final MeshModels models;
    private final Runnable whenBaked;

    public BladePass(MeshScratch scratch, MeshModels models, Runnable whenBaked) {
        this.scratch = scratch;
        this.models = models;
        this.whenBaked = whenBaked;
    }

    public boolean run() {
        CellVoxels voxels = scratch.voxels();

        for (int y = 0; y < SIDE; y++) {
            for (int z = 0; z < SIDE; z++) {
                for (int x = 0; x < SIDE; x++) {
                    long entry = voxels.inside(x, y, z);
                    if (VoxelEntry.isAir(entry)) {
                        continue;
                    }

                    int modelId = models.modelId(VoxelEntry.state(entry), whenBaked);
                    if (modelId == MeshModels.MISSING) {
                        return false;
                    }

                    int metadata = models.metadata(modelId);
                    if (ModelMetadata.has(metadata, ModelMetadata.BLADED)) {
                        emit(entry, metadata, modelId, x, y, z);
                    }
                }
            }
        }

        return true;
    }

    private void emit(long entry, int metadata, int modelId, int x, int y, int z) {
        if (!Quad.fitsModelId(modelId)) {
            scratch.buffer().dropUnaddressable();
            return;
        }

        int colourIndex = QuadTint.of(scratch, models, VoxelEntry.state(entry), modelId, x, y, z);
        long data = Quad.data(QuadLight.of(entry, metadata), modelId, colourIndex);
        int group = QuadGroups.ofBlade(metadata);

        for (int blade = 0; blade < Quad.BLADE_COUNT; blade++) {
            scratch.buffer().add(group, Quad.of(data, Quad.bladeFace(blade), x, y, z, ONE_VOXEL, ONE_VOXEL));
        }
    }
}
