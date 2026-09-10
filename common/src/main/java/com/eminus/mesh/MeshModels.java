package com.eminus.mesh;

public interface MeshModels {
    int MISSING = -1;

    int modelId(int stateId, Runnable whenBaked);

    int metadata(int modelId);
}
