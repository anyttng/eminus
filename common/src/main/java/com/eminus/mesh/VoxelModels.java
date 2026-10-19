package com.eminus.mesh;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;

public final class VoxelModels {
    private CellFrame frame;
    private long key;
    private boolean coarse;

    public void begin(CellFrame frame, long key) {
        this.frame = frame;
        this.key = key;
        coarse = CellKey.level(key) > DetailLevel.MIN;
    }

    public int at(MeshModels models, int stateId, int x, int y, int z, Runnable whenBaked) {
        int modelId = models.modelId(stateId, whenBaked);
        if (modelId == MeshModels.POSITIONAL) {
            modelId = models.positionalModelId(stateId, CellFrame.blockXOf(key, x), frame.blockYOf(key, y),
                    CellFrame.blockZOf(key, z), whenBaked);
        }

        return coarse ? models.opaqueModelId(modelId) : modelId;
    }
}
