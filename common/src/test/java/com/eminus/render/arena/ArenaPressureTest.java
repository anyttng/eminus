package com.eminus.render.arena;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArenaPressureTest {
    private static final int BLOCKS = 1000;
    private static final int HIGH = 850;
    private static final int BETWEEN = 800;
    private static final int LOW = 750;
    private static final int BELOW_LOW = 749;

    private final ArenaPressure pressure = new ArenaPressure();

    @Test
    void theHighWaterMarkStartsTheHold() {
        pressure.update(HIGH - 1, BLOCKS, false);
        assertFalse(pressure.holding());

        pressure.update(HIGH, BLOCKS, false);
        assertTrue(pressure.holding());
    }

    @Test
    void aRefusalStartsTheHoldBelowTheMark() {
        pressure.update(BETWEEN, BLOCKS, true);

        assertTrue(pressure.holding());
    }

    @Test
    void theHoldLastsUntilTheArenaFallsBelowTheLowWaterMark() {
        pressure.update(HIGH, BLOCKS, false);

        pressure.update(BETWEEN, BLOCKS, false);
        assertTrue(pressure.holding());

        pressure.update(LOW, BLOCKS, false);
        assertTrue(pressure.holding());

        pressure.update(BELOW_LOW, BLOCKS, false);
        assertFalse(pressure.holding());
    }

    @Test
    void betweenTheMarksAnArenaThatWasNotHoldingStaysFree() {
        pressure.update(BETWEEN, BLOCKS, false);

        assertFalse(pressure.holding());
    }
}
