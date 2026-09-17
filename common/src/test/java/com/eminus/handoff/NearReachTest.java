package com.eminus.handoff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NearReachTest {
    private static final int FIFTEEN_CHUNKS = 15;
    private static final int OVERWORLD_MIN_Y = -64;
    private static final int OVERWORLD_END_Y = 320;
    private static final float TOLERANCE = 1.0E-2F;

    @Test
    void reachesTheFarCornerOfTheLastKeptChunkAndTheFartherWorldEdge() {
        float expected = (float) Math.sqrt(256.0 * 256.0 + 256.0 * 256.0 + 264.0 * 264.0);

        assertEquals(expected, NearReach.blocks(FIFTEEN_CHUNKS, 200.0, OVERWORLD_MIN_Y, OVERWORLD_END_Y), TOLERANCE);
    }

    @Test
    void aCameraLowInTheWorldMeasuresToItsTop() {
        float expected = (float) Math.sqrt(256.0 * 256.0 + 256.0 * 256.0 + 320.0 * 320.0);

        assertEquals(expected, NearReach.blocks(FIFTEEN_CHUNKS, 0.0, OVERWORLD_MIN_Y, OVERWORLD_END_Y), TOLERANCE);
    }
}
