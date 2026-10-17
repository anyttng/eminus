package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

class MeshBufferTest {
    private static final int WATER_BLUE = 0x3F76E4;
    private static final int SWAMP_GREEN = 0x6A7039;
    private static final int CORNERS = 0x7F7F_7F80;
    private static final int UP = Direction.UP.get3DDataValue();
    private static final long KEY = CellKey.pack(DetailLevel.MIN, 0, 0, 0);
    private static final int LIGHT = 0xF0;
    private static final int MODEL = 7;
    private static final int WHITE_ENTRY = 0;

    @Test
    void cornersTakeTheirOwnEntryWhileTheTableHasRoom() {
        MeshBuffer buffer = new MeshBuffer();
        int flat = buffer.colourIndex(WATER_BLUE, QuadOffset.NONE);

        assertNotEquals(flat, buffer.colourIndex(WATER_BLUE, CORNERS));
    }

    @Test
    void cornersOverAFullTableFallBackToTheFlatEntryOfTheirColour() {
        MeshBuffer buffer = new MeshBuffer();
        int flat = buffer.colourIndex(WATER_BLUE, QuadOffset.NONE);
        fill(buffer);

        assertEquals(flat, buffer.colourIndex(WATER_BLUE, CORNERS));
        assertTrue(buffer.lostPlacements());
    }

    @Test
    void aFullTableSubstitutesTheColourOfAPlacementItHolds() {
        MeshBuffer buffer = new MeshBuffer();
        buffer.colourIndex(WATER_BLUE, CORNERS);
        fill(buffer);

        assertEquals(CORNERS, buffer.placementAt(buffer.colourIndex(SWAMP_GREEN, CORNERS)));
        assertFalse(buffer.lostPlacements());
    }

    @Test
    void aReservingResetGivesALostPlacementItsEntryBeforeTheTableFills() {
        MeshBuffer buffer = new MeshBuffer();
        fill(buffer);
        buffer.colourIndex(WATER_BLUE, CORNERS);

        buffer.resetReserving();
        fill(buffer);

        assertEquals(CORNERS, buffer.placementAt(buffer.colourIndex(WATER_BLUE, CORNERS)));
        assertFalse(buffer.lostPlacements());
    }

    @Test
    void aMeshWithoutAWordCarriesNoStream() {
        MeshBuffer buffer = new MeshBuffer();
        buffer.add(UP, quad(0), OcclusionWord.NONE);
        buffer.add(UP, quad(1), OcclusionWord.NONE);

        CellMesh mesh = buffer.freeze(KEY, OccupancyMask.EMPTY);

        assertEquals(0, mesh.occlusion().length);
        assertEquals(OcclusionWord.NONE, mesh.occlusion(1));
        assertEquals(mesh.quadCount() + mesh.colours().length, mesh.slotCount());
    }

    @Test
    void oneWordKeepsTheWholeStream() {
        MeshBuffer buffer = new MeshBuffer();
        buffer.add(UP, quad(0), OcclusionWord.NONE);
        buffer.add(UP, quad(1), OcclusionWord.LOW_U);

        CellMesh mesh = buffer.freeze(KEY, OccupancyMask.EMPTY);

        assertEquals(OcclusionWord.slots(2), mesh.occlusion().length);
        assertEquals(OcclusionWord.LOW_U, mesh.occlusion(1));
    }

    private static long quad(int x) {
        return Quad.of(Quad.data(LIGHT, MODEL, WHITE_ENTRY), UP, x, 0, 0, 1, 1);
    }

    private static void fill(MeshBuffer buffer) {
        for (int colour = 1; colour < MeshBuffer.MAX_COLOURS; colour++) {
            buffer.colourIndex(colour, QuadOffset.NONE);
        }
    }
}
