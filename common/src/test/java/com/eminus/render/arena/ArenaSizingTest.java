package com.eminus.render.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.settings.Settings;

import org.junit.jupiter.api.Test;

class ArenaSizingTest {
    private static final long ROOMY_DEVICE = 8L * 1024 * 1024 * 1024;
    private static final long CRAMPED_DEVICE = 64L * 1024 * 1024;

    @Test
    void theSmallestRenderDistanceStillAsksForTheMinimum() {
        assertEquals(ArenaSizing.MIN_BYTES, ArenaSizing.wanted(Settings.MIN_FAR_RENDER_CELLS));
    }

    @Test
    void theLargestRenderDistanceIsCappedAtTheMaximum() {
        assertEquals(ArenaSizing.MAX_BYTES, ArenaSizing.wanted(Settings.MAX_FAR_RENDER_CELLS));
    }

    @Test
    void theDefaultRenderDistanceLandsBetweenTheBounds() {
        long bytes = ArenaSizing.wanted(Settings.DEFAULT_FAR_RENDER_CELLS);

        assertTrue(bytes > ArenaSizing.MIN_BYTES, "The default asks for more than the minimum: " + bytes);
        assertTrue(bytes < ArenaSizing.MAX_BYTES, "The default asks for less than the maximum: " + bytes);
    }

    @Test
    void aRoomyDeviceGetsWhatWasWanted() {
        long wanted = ArenaSizing.wanted(Settings.DEFAULT_FAR_RENDER_CELLS);

        assertEquals(wanted, ArenaSizing.fitted(wanted, ROOMY_DEVICE));
    }

    @Test
    void aDeviceShareBelowTheMinimumRefusesTheArena() {
        assertEquals(ArenaSizing.REFUSED, ArenaSizing.fitted(ArenaSizing.MAX_BYTES, CRAMPED_DEVICE));
    }

    @Test
    void theFittedSizeIsAWholeNumberOfBlocks() {
        long unaligned = ArenaSizing.MIN_BYTES + ArenaSizing.BLOCK_BYTES + 1L;
        long fitted = ArenaSizing.fitted(unaligned, ROOMY_DEVICE);

        assertEquals(0L, fitted % ArenaSizing.BLOCK_BYTES);
        assertEquals(fitted / ArenaSizing.BLOCK_BYTES, ArenaSizing.blocks(fitted));
    }
}
