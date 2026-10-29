package com.eminus.render.arena;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArenaPressureTest {
    private static final int SEGMENTS = 1000;
    private static final int HIGH = 850;
    private static final int BETWEEN = 800;
    private static final int LOW = 750;
    private static final int BELOW_LOW = 749;

    private final ArenaPressure pressure = new ArenaPressure();

    @Test
    void theHighWaterMarkStartsTheHold() {
        pressure.update(HIGH - 1, SEGMENTS, false);
        assertFalse(pressure.holding());

        pressure.update(HIGH, SEGMENTS, false);
        assertTrue(pressure.holding());
    }

    @Test
    void aRefusalStartsTheHoldBelowTheMark() {
        pressure.update(BETWEEN, SEGMENTS, true);

        assertTrue(pressure.holding());
    }

    @Test
    void theHoldLastsUntilTheArenaFallsBelowTheLowWaterMark() {
        pressure.update(HIGH, SEGMENTS, false);

        pressure.update(BETWEEN, SEGMENTS, false);
        assertTrue(pressure.holding());

        pressure.update(LOW, SEGMENTS, false);
        assertTrue(pressure.holding());

        pressure.update(BELOW_LOW, SEGMENTS, false);
        assertFalse(pressure.holding());
    }

    @Test
    void betweenTheMarksAnArenaThatWasNotHoldingStaysFree() {
        pressure.update(BETWEEN, SEGMENTS, false);

        assertFalse(pressure.holding());
    }

    @Test
    void onlyTheUpdateThatStartsTheHoldReportsIt() {
        assertFalse(pressure.update(HIGH - 1, SEGMENTS, false));

        assertTrue(pressure.update(HIGH, SEGMENTS, false));
        assertFalse(pressure.update(HIGH, SEGMENTS, false));
        assertFalse(pressure.update(BETWEEN, SEGMENTS, false));
    }

    @Test
    void aHoldStartedAgainAfterTheArenaFreedUpReportsItAgain() {
        pressure.update(HIGH, SEGMENTS, false);
        pressure.update(BELOW_LOW, SEGMENTS, false);

        assertTrue(pressure.update(HIGH, SEGMENTS, false));
    }
}
