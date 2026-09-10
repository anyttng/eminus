package com.eminus.render.arena;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.mesh.CellMesh;

public record MeshSlot(long key, int block, int quads, int[] groupStart, int[] groupCount) {
    public static final int MIN_X = 0;
    public static final int MIN_Y = 1;
    public static final int MIN_Z = 2;
    public static final int MAX_X = 3;
    public static final int MAX_Y = 4;
    public static final int MAX_Z = 5;
    public static final int BOUNDS = 6;

    public static MeshSlot of(CellMesh mesh, int block) {
        return new MeshSlot(mesh.key(), block, mesh.quadCount(), mesh.groupStart(), mesh.groupCount());
    }

    public int baseQuad() {
        return block * ArenaAllocator.QUADS_PER_BLOCK;
    }

    public void bounds(CellFrame frame, float[] target) {
        int level = CellKey.level(key);
        int side = DetailLevel.blocksPerCell(level);

        target[MIN_X] = frame.originBlockX(CellKey.x(key), level);
        target[MIN_Y] = frame.originBlockY(CellKey.y(key), level);
        target[MIN_Z] = frame.originBlockZ(CellKey.z(key), level);
        target[MAX_X] = target[MIN_X] + side;
        target[MAX_Y] = target[MIN_Y] + side;
        target[MAX_Z] = target[MIN_Z] + side;
    }
}
