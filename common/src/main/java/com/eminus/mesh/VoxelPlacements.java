package com.eminus.mesh;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.VoxelEntry;

public final class VoxelPlacements {
    private CellFrame frame;
    private long key;
    private int level;

    public void begin(CellFrame frame, long key) {
        this.frame = frame;
        this.key = key;
        level = CellKey.level(key);
    }

    public int block(MeshModels models, long entry, int x, int y, int z) {
        int offset = QuadPlacement.gapped(level)
                ? QuadOffset.NONE
                : models.offset(VoxelEntry.state(entry), CellFrame.blockXOf(key, x), frame.blockYOf(key, y),
                        CellFrame.blockZOf(key, z));
        return QuadPlacement.ofBlock(level, entry, offset);
    }

    public int fluid(long entry, int corners) {
        return QuadPlacement.ofFluid(level, entry, corners);
    }
}
