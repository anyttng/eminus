package com.eminus.render.arena;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.mesh.CellMesh;

@SuppressWarnings("ArrayRecordComponent")
public record MeshSlot(long key, int block, int quads, int occlusion, int colors, int[] groupStart,
        int[] groupCount, int[] groupSolid) {
    public static final int MIN_X = 0;
    public static final int MIN_Y = 1;
    public static final int MIN_Z = 2;
    public static final int MAX_X = 3;
    public static final int MAX_Y = 4;
    public static final int MAX_Z = 5;
    public static final int BOUNDS = 6;

    public static MeshSlot of(CellMesh mesh, int block) {
        return new MeshSlot(mesh.key(), block, mesh.quadCount(), mesh.occlusion().length, mesh.colors().length,
                mesh.groupStart(), mesh.groupCount(), mesh.groupSolid());
    }

    public MeshSlot at(int block) {
        return new MeshSlot(key, block, quads, occlusion, colors, groupStart, groupCount, groupSolid);
    }

    public int slots() {
        return quads + occlusion + colors;
    }

    public boolean occluded() {
        return occlusion > 0;
    }

    public int baseQuad() {
        return block * ArenaAllocator.QUADS_PER_BLOCK;
    }

    public int groupStart(int group) {
        return groupStart[group];
    }

    public int groupCount(int group) {
        return groupCount[group];
    }

    public int groupSolid(int group) {
        return groupSolid[group];
    }

    public void bounds(CellFrame frame, float[] target) {
        int side = DetailLevel.blocksPerCell(CellKey.level(key));

        target[MIN_X] = CellFrame.originXOf(key);
        target[MIN_Y] = frame.originYOf(key);
        target[MIN_Z] = CellFrame.originZOf(key);
        target[MAX_X] = target[MIN_X] + side;
        target[MAX_Y] = target[MIN_Y] + side;
        target[MAX_Z] = target[MIN_Z] + side;
    }
}
