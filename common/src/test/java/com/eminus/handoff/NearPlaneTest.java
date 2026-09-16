package com.eminus.handoff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NearPlaneTest {
    private static final int TWO_CHUNKS = 2;
    private static final int THREE_CHUNKS = 3;
    private static final int TWELVE_CHUNKS = 12;

    @Test
    void twoChunksHalveTheNearPlane() {
        assertEquals(NearPlane.SHORT_BLOCKS, NearPlane.blocks(TWO_CHUNKS));
    }

    @Test
    void anyLongerRenderDistanceKeepsTheFullNearPlane() {
        assertEquals(NearPlane.BLOCKS, NearPlane.blocks(THREE_CHUNKS));
        assertEquals(NearPlane.BLOCKS, NearPlane.blocks(TWELVE_CHUNKS));
    }
}
