package com.eminus.render.arena;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.OcclusionWord;
import com.eminus.mesh.QuadGroups;
import com.eminus.mesh.QuadPieces;

@SuppressWarnings("ArrayRecordComponent")
public record MeshSlot(long key, int segment, int quads, int occlusion, int colors, int[] groupStart,
        int[] groupCount, int[] groupSolid, int[] pieceStart, int[] pieceCount, int[] piecesBefore) {
    public static final int MIN_X = 0;
    public static final int MIN_Y = 1;
    public static final int MIN_Z = 2;
    public static final int MAX_X = 3;
    public static final int MAX_Y = 4;
    public static final int MAX_Z = 5;
    public static final int BOUNDS = 6;

    public static MeshSlot of(CellMesh mesh, int segment) {
        int[] before = QuadPieces.before(mesh.quads(), mesh.quadCount());
        int[] pieceStart = new int[QuadGroups.COUNT];
        int[] pieceCount = new int[QuadGroups.COUNT];
        for (int group = 0; group < QuadGroups.COUNT; group++) {
            pieceStart[group] = before[mesh.groupStart(group)];
            pieceCount[group] = before[mesh.groupStart(group) + mesh.groupCount(group)] - pieceStart[group];
        }
        return new MeshSlot(mesh.key(), segment, mesh.quadCount(), mesh.occlusion().length, mesh.colors().length,
                mesh.groupStart(), mesh.groupCount(), mesh.groupSolid(), pieceStart, pieceCount,
                QuadPieces.entries(before, mesh.quadCount()));
    }

    public MeshSlot at(int segment) {
        return new MeshSlot(key, segment, quads, occlusion, colors, groupStart, groupCount, groupSolid, pieceStart,
                pieceCount, piecesBefore);
    }

    public int slots() {
        return quads + occlusion + colors;
    }

    public boolean occluded() {
        return occlusion > 0;
    }

    public boolean innerOccluded() {
        return occlusion > OcclusionWord.slots(quads);
    }

    public int baseQuad() {
        return segment * ArenaAllocator.QUADS_PER_SEGMENT;
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

    public int pieceStart(int group) {
        return pieceStart[group];
    }

    public int pieceCount(int group) {
        return pieceCount[group];
    }

    public int piecesBefore(int entry) {
        return entry < piecesBefore.length ? piecesBefore[entry] : 0;
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
