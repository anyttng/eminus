package com.eminus.handoff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NearSectionsTest {
    private static final int RADIUS = 1;
    private static final int MIN_SECTION_Y = -4;
    private static final int SECTION_COUNT = 24;
    private static final int TALLEST_SECTION_COUNT = 254;
    private static final int THIRTY_FIVE_CHUNKS = 35;
    private static final int TWELVE_CHUNKS = 12;
    private static final int GUARANTEED_TEXELS = 65_536;

    @Test
    void sectionsOfNegativeBlocksRoundDown() {
        assertEquals(0, NearSections.section(15));
        assertEquals(-1, NearSections.section(-1));
        assertEquals(-1, NearSections.section(-16));
        assertEquals(-2, NearSections.section(-17));
    }

    @Test
    void indexRunsYInsideXInsideZ() {
        NearSections sections = window();

        assertEquals(0, sections.index(-1, -4, -1));
        assertEquals(1, sections.index(-1, -3, -1));
        assertEquals(24, sections.index(0, -4, -1));
        assertEquals(72, sections.index(-1, -4, 0));
        assertEquals(215, sections.index(1, 19, 1));
    }

    @Test
    void sectionsPastTheWindowAreOutside() {
        NearSections sections = window();

        assertEquals(NearSections.OUTSIDE, sections.index(2, 0, 0));
        assertEquals(NearSections.OUTSIDE, sections.index(0, 0, -2));
        assertEquals(NearSections.OUTSIDE, sections.index(0, -5, 0));
        assertEquals(NearSections.OUTSIDE, sections.index(0, 20, 0));
        assertFalse(sections.owned(2, 0, 0));
    }

    @Test
    void aBlockBoxAsksEachSectionInsideTheWindowOnce() {
        NearSections sections = window();
        int[] asked = new int[1];
        NearSections.SectionQuery groundOnly = (x, y, z) -> {
            asked[0]++;
            return y == 0;
        };

        sections.queryBlocks(-40, 0, -40, 40, 32, 40, groundOnly);
        sections.queryBlocks(0, 0, 0, 16, 16, 16, groundOnly);

        assertEquals(18, asked[0]);
        assertEquals(18, sections.queried());
        assertEquals(9, sections.built());
        assertTrue(sections.owned(0, 0, 0));
        assertTrue(sections.owned(-1, 0, 1));
        assertFalse(sections.owned(0, 1, 0));
        assertFalse(sections.owned(0, 2, 0));
    }

    @Test
    void aBoxEndingOnASectionBoundaryStopsBeforeIt() {
        NearSections sections = window();
        int[] asked = new int[1];

        sections.queryBlocks(0, 0, 0, 16, 16, 16, (x, y, z) -> {
            asked[0]++;
            return true;
        });

        assertEquals(1, asked[0]);
    }

    @Test
    void resetForgetsTheLastWindow() {
        NearSections sections = window();
        sections.queryBlocks(0, 0, 0, 16, 16, 16, (x, y, z) -> true);

        sections.reset(0, 0, RADIUS, MIN_SECTION_Y, SECTION_COUNT);

        assertFalse(sections.owned(0, 0, 0));
        assertEquals(0, sections.queried());
        assertEquals(0, sections.built());
    }

    @Test
    void theTallestDimensionAtThirtyFiveChunksFitsTheGuaranteedTexelBuffer() {
        NearSections sections = new NearSections();

        sections.reset(0, 0, THIRTY_FIVE_CHUNKS, MIN_SECTION_Y, TALLEST_SECTION_COUNT);

        assertEquals(40_013, sections.texels());
        assertTrue(sections.texels() <= GUARANTEED_TEXELS);
    }

    @Test
    void theOriginIsTheWindowsLowestCornerInBlocks() {
        NearSections sections = window();

        assertEquals(-16, sections.originBlockX());
        assertEquals(-64, sections.originBlockY());
        assertEquals(-16, sections.originBlockZ());
    }

    @Test
    void vanillaViewDistanceGivesOneChunkOfSlackAroundTheCircle() {
        assertTrue(NearSections.inVanillaViewDistance(0, 0, 0, TWELVE_CHUNKS, 12, 0, 0));
        assertFalse(NearSections.inVanillaViewDistance(0, 0, 0, TWELVE_CHUNKS, 13, 0, 0));
        assertTrue(NearSections.inVanillaViewDistance(0, 0, 0, TWELVE_CHUNKS, 9, 0, -9));
        assertFalse(NearSections.inVanillaViewDistance(0, 0, 0, TWELVE_CHUNKS, 10, 0, -10));
    }

    @Test
    void vanillaViewDistanceCutsAsManySectionsVertically() {
        assertTrue(NearSections.inVanillaViewDistance(0, 0, 0, TWELVE_CHUNKS, 0, 12, 0));
        assertFalse(NearSections.inVanillaViewDistance(0, 0, 0, TWELVE_CHUNKS, 0, -13, 0));
    }

    @Test
    void vanillaViewDistanceIsMeasuredFromTheCameraSection() {
        assertTrue(NearSections.inVanillaViewDistance(100, 5, -40, TWELVE_CHUNKS, 112, 5, -40));
        assertFalse(NearSections.inVanillaViewDistance(100, 5, -40, TWELVE_CHUNKS, 113, 5, -40));
    }

    private static NearSections window() {
        NearSections sections = new NearSections();
        sections.reset(0, 0, RADIUS, MIN_SECTION_Y, SECTION_COUNT);
        return sections;
    }
}
