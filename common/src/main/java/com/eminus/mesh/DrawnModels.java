package com.eminus.mesh;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;

final class DrawnModels {
    static final int NO_SEABED = -5;

    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int FIRST_LAYER = 0;
    private static final int LAST_LAYER = SIDE - 1;
    private static final int LAYER_BELOW = -1;
    private static final int LAYER_ABOVE = SIDE;

    private final MeshScratch scratch;
    private final MeshModels models;
    private final Runnable whenBaked;

    private long seabedLight;

    DrawnModels(MeshScratch scratch, MeshModels models, Runnable whenBaked) {
        this.scratch = scratch;
        this.models = models;
        this.whenBaked = whenBaked;
    }

    int at(int state, int atX, int atY, int atZ) {
        return scratch.voxelModels().at(models, state, atX, atY, atZ, whenBaked);
    }

    int facing(long entry, int atX, int atY, int atZ) {
        return VoxelEntry.isAir(entry) ? MeshModels.AIR : at(VoxelEntry.state(entry), atX, atY, atZ);
    }

    int facingFluid(long entry, int atX, int atY, int atZ) {
        if (VoxelEntry.isAir(entry)) {
            return MeshModels.AIR;
        }

        int state = VoxelEntry.state(entry);
        int fluidModel = models.fluidModelId(state, whenBaked);
        return fluidModel == MeshModels.NO_FLUID ? at(state, atX, atY, atZ) : fluidModel;
    }

    int drawn(int surfaceModel, int atX, int atY, int atZ) {
        int submergedModel = models.submergedModelId(surfaceModel);
        if (submergedModel == surfaceModel) {
            return surfaceModel;
        }

        if (!held(atX, atY + 1, atZ)) {
            return surfaceModel;
        }

        long above = scratch.voxels().entry(atX, atY + 1, atZ);
        int aboveModel = facingFluid(above, atX, atY + 1, atZ);
        if (aboveModel == MeshModels.MISSING) {
            return MeshModels.MISSING;
        }

        return models.submergedModelId(aboveModel) == submergedModel ? submergedModel : surfaceModel;
    }

    int seabed(int atX, int atY, int atZ) {
        CellVoxels voxels = scratch.voxels();
        long above = voxels.inside(atX, atY, atZ);
        for (int down = atY - 1; down >= FIRST_LAYER; down--) {
            long entry = voxels.inside(atX, down, atZ);
            if (solid(entry)) {
                seabedLight = above;
                return at(VoxelEntry.state(entry), atX, down, atZ);
            }

            above = entry;
        }

        if (!voxels.belowLoaded()) {
            return NO_SEABED;
        }

        for (int down = LAST_LAYER; down >= FIRST_LAYER; down--) {
            long entry = voxels.below(atX, down, atZ);
            if (solid(entry)) {
                seabedLight = above;
                return at(VoxelEntry.state(entry), atX, down - SIDE, atZ);
            }

            above = entry;
        }

        return NO_SEABED;
    }

    long seabedLight() {
        return seabedLight;
    }

    private boolean solid(long entry) {
        return !VoxelEntry.isAir(entry) && models.solid(VoxelEntry.state(entry));
    }

    private static boolean held(int atX, int atY, int atZ) {
        return outside(atX) + outside(atY) + outside(atZ) <= 1 && reaches(atX) && reaches(atY) && reaches(atZ);
    }

    private static int outside(int at) {
        return at < 0 || at >= SIDE ? 1 : 0;
    }

    private static boolean reaches(int at) {
        return at >= LAYER_BELOW && at <= LAYER_ABOVE;
    }
}
