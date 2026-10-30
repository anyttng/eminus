package com.eminus.mesh;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

public final class BladePass {
    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int ONE_VOXEL = 1;
    private static final Direction[] FACES = Direction.values();

    private final MeshScratch scratch;
    private final MeshModels models;
    private final Runnable whenBaked;
    private final FaceOcclusion occlusion;

    public BladePass(MeshScratch scratch, MeshModels models, int level, boolean occluded, Runnable whenBaked) {
        this.scratch = scratch;
        this.models = models;
        this.whenBaked = whenBaked;
        occlusion = new FaceOcclusion(scratch.voxels(), models, level, occluded);
    }

    public boolean run() {
        CellVoxels voxels = scratch.voxels();

        for (int y = 0; y < SIDE; y++) {
            for (int z = 0; z < SIDE; z++) {
                for (int x = 0; x < SIDE; x++) {
                    long entry = voxels.entry(x, y, z);
                    if (VoxelEntry.isAir(entry)) {
                        continue;
                    }

                    int modelId = scratch.voxelModels().at(models, VoxelEntry.state(entry), x, y, z, whenBaked);
                    if (modelId == MeshModels.MISSING) {
                        return false;
                    }

                    int metadata = models.metadata(modelId);
                    if (ModelMetadata.has(metadata, ModelMetadata.BLADED)) {
                        blades(entry, metadata, modelId, x, y, z);
                    }

                    for (int pieceId : models.pieceIds(modelId)) {
                        piece(entry, pieceId, x, y, z);
                    }
                }
            }
        }

        return true;
    }

    private void piece(long entry, int modelId, int x, int y, int z) {
        int metadata = models.metadata(modelId);
        if (ModelMetadata.has(metadata, ModelMetadata.BLADED)) {
            blades(entry, metadata, modelId, x, y, z);
        } else {
            faces(entry, metadata, modelId, x, y, z);
        }
    }

    private void blades(long entry, int metadata, int modelId, int x, int y, int z) {
        if (!Quad.fitsModelId(modelId)) {
            scratch.buffer().dropUnaddressable();
            return;
        }

        long data = data(entry, metadata, modelId, x, y, z);
        int group = QuadGroups.ofBlade(metadata);
        int word = occlusion.blade(x, y, z, metadata);

        for (int blade = 0; blade < Quad.BLADE_COUNT; blade++) {
            scratch.buffer().add(group, Quad.of(data, Quad.bladeFace(blade), x, y, z, ONE_VOXEL, ONE_VOXEL), word);
        }
    }

    private void faces(long entry, int metadata, int modelId, int x, int y, int z) {
        if (!Quad.fitsModelId(modelId)) {
            scratch.buffer().dropUnaddressable();
            return;
        }

        long data = data(entry, metadata, modelId, x, y, z);
        int present = ModelMetadata.present(metadata);
        int solid = models.solidFaces(modelId);

        for (Direction face : FACES) {
            int index = face.get3DDataValue();
            if ((present & 1 << index) == 0) {
                continue;
            }

            int word = occlusion.face(face, x, y, z, VoxelEntry.state(entry), metadata, models.inset(modelId, index));
            scratch.buffer().add(QuadGroups.of(face, metadata), Quad.of(data, index, x, y, z, ONE_VOXEL, ONE_VOXEL),
                    word, (solid & 1 << index) != 0);
        }
    }

    private long data(long entry, int metadata, int modelId, int x, int y, int z) {
        int colorIndex = QuadTint.of(scratch, models, entry, modelId, x, y, z);
        return Quad.data(QuadLight.of(entry, metadata), modelId, colorIndex);
    }
}
