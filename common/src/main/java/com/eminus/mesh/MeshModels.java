package com.eminus.mesh;

public interface MeshModels {
    int MISSING = -1;
    int NO_FLUID = -2;
    int POSITIONAL = -4;

    int modelId(int stateId, Runnable whenBaked);

    int positionalModelId(int stateId, int blockX, int blockY, int blockZ, Runnable whenBaked);

    int fluidModelId(int stateId, Runnable whenBaked);

    int submergedModelId(int modelId);

    int metadata(int modelId);

    int tintRow(int modelId);

    int offset(int stateId, int blockX, int blockY, int blockZ);
}
