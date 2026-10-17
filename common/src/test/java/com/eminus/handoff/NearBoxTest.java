package com.eminus.handoff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NearBoxTest {
    private static final int RADIUS = 2;
    private static final int CELL = 32;
    private static final int BOX_MIN = -32;
    private static final int BOX_END = 48;
    private static final NearBox BOX = NearBox.around(0, 0, RADIUS);

    @Test
    void theBoxSpansTheRadiusOfSectionsOnEachSideOfItsCentre() {
        assertEquals(new NearBox(BOX_MIN, BOX_MIN, BOX_END, BOX_END), BOX);
    }

    @Test
    void aCellTouchingTheBoxMeetsItBecauseAFaceOnTheSharedPlaneFloorsIntoTheBox() {
        assertTrue(BOX.meets(BOX_MIN - CELL, 0, CELL));
        assertTrue(BOX.meets(0, BOX_END, CELL));
    }

    @Test
    void aCellOneBlockClearOfTheBoxDoesNotMeetIt() {
        assertFalse(BOX.meets(BOX_MIN - 1 - CELL, 0, CELL));
        assertFalse(BOX.meets(0, BOX_END + 1, CELL));
    }
}
