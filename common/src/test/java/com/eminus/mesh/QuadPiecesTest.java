package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;
import com.eminus.render.arena.MeshSlot;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

class QuadPiecesTest {
    private static final int NO_DATA = 0;
    private static final int WIDE = 5;
    private static final int TALL = 3;
    private static final int QUADS = 2 * QuadPieces.QUADS_PER_ENTRY + 1;
    private static final int SEGMENT = 4;

    @Test
    void aQuadHoldsOnePiecePerVoxelFace() {
        assertEquals(WIDE * TALL, QuadPieces.of(quad(Direction.UP, WIDE, TALL)));
        assertEquals(1, QuadPieces.of(quad(Direction.UP, 1, 1)));
    }

    @Test
    void anEntryHoldsThePiecesBeforeEveryEighthQuad() {
        long[] quads = new long[QUADS];
        for (int quad = 0; quad < QUADS; quad++) {
            quads[quad] = quad(Direction.UP, WIDE, TALL);
        }
        int[] before = QuadPieces.before(quads, QUADS);

        int perEntry = QuadPieces.QUADS_PER_ENTRY * WIDE * TALL;
        assertEquals(QUADS * WIDE * TALL, before[QUADS]);
        assertArrayEquals(new int[] {0, perEntry, 2 * perEntry}, QuadPieces.entries(before, QUADS));
    }

    @Test
    void aSlotCountsEachGroupsPiecesFromTheMeshsFirst() {
        int[] groupStart = new int[QuadGroups.COUNT];
        int[] groupCount = new int[QuadGroups.COUNT];
        int down = Direction.DOWN.get3DDataValue();
        int up = Direction.UP.get3DDataValue();
        groupCount[down] = 1;
        groupStart[up] = 1;
        groupCount[up] = 2;
        long[] quads = {quad(Direction.DOWN, WIDE, TALL), quad(Direction.UP, 1, 1), quad(Direction.UP, WIDE, 1)};
        CellMesh mesh = new CellMesh(CellKey.pack(DetailLevel.MIN, 0, 0, 0), OccupancyMask.EMPTY, quads, groupStart,
                groupCount, new long[0]);

        MeshSlot slot = MeshSlot.of(mesh, SEGMENT);

        assertEquals(0, slot.pieceStart(down));
        assertEquals(WIDE * TALL, slot.pieceCount(down));
        assertEquals(WIDE * TALL, slot.pieceStart(up));
        assertEquals(1 + WIDE, slot.pieceCount(up));
        assertEquals(0, slot.piecesBefore(0));
        assertEquals(0, slot.piecesBefore(1));
        assertEquals(mesh.slotCount(), slot.slots());
    }

    private static long quad(Direction face, int width, int height) {
        return Quad.of(NO_DATA, face.get3DDataValue(), 0, 0, 0, width, height);
    }
}
