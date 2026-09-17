package com.eminus.mesh;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;

public final class VoxelOffsets {
    private static final int OFFSET_LEVEL = 0;

    private CellFrame frame;
    private long key;
    private boolean offsetting;

    public void begin(CellFrame frame, long key) {
        this.frame = frame;
        this.key = key;
        offsetting = CellKey.level(key) == OFFSET_LEVEL;
    }

    public int at(MeshModels models, int stateId, int x, int y, int z) {
        if (!offsetting) {
            return QuadOffset.NONE;
        }

        return models.offset(stateId, frame.blockXOf(key, x), frame.blockYOf(key, y), frame.blockZOf(key, z));
    }
}
