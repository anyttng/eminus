package com.eminus.mesh;

import com.eminus.cell.CellFrame;

public final class VoxelModels {
    private CellFrame frame;
    private long key;

    public void begin(CellFrame frame, long key) {
        this.frame = frame;
        this.key = key;
    }

    public int at(MeshModels models, int stateId, int x, int y, int z, Runnable whenBaked) {
        int modelId = models.modelId(stateId, whenBaked);
        if (modelId != MeshModels.POSITIONAL) {
            return modelId;
        }

        return models.positionalModelId(stateId, frame.blockXOf(key, x), frame.blockYOf(key, y),
                frame.blockZOf(key, z), whenBaked);
    }
}
