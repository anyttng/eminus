package com.eminus.handoff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.IntBuffer;

import org.junit.jupiter.api.Test;

class CoverageSectionsTest {
    private static final int CAPACITY = 2;
    private static final int ORIGIN_X = 16;
    private static final int ORIGIN_Y = -64;
    private static final int ORIGIN_Z = -32;
    private static final int SECOND_X = 32;
    private static final int THIRD_Z = 48;

    private final CoverageSections sections = new CoverageSections(CAPACITY);

    @Test
    void aSectionIsWrittenAsItsOriginAndOneUnusedInt() {
        sections.add(ORIGIN_X, ORIGIN_Y, ORIGIN_Z);

        IntBuffer written = sections.buffer().asIntBuffer();
        assertEquals(1, sections.count());
        assertEquals(CoverageSections.SECTION_INTS, written.remaining());
        assertEquals(ORIGIN_X, written.get(0));
        assertEquals(ORIGIN_Y, written.get(1));
        assertEquals(ORIGIN_Z, written.get(2));
    }

    @Test
    void addingPastTheCapacityGrowsAndKeepsWhatWasWritten() {
        sections.add(ORIGIN_X, ORIGIN_Y, ORIGIN_Z);
        sections.add(SECOND_X, ORIGIN_Y, ORIGIN_Z);
        sections.add(ORIGIN_X, ORIGIN_Y, THIRD_Z);

        IntBuffer written = sections.buffer().asIntBuffer();
        assertEquals(3, sections.count());
        assertTrue(sections.capacity() >= 3);
        assertEquals(3 * CoverageSections.SECTION_INTS, written.remaining());
        assertEquals(ORIGIN_X, written.get(0));
        assertEquals(SECOND_X, written.get(CoverageSections.SECTION_INTS));
        assertEquals(THIRD_Z, written.get(2 * CoverageSections.SECTION_INTS + 2));
    }

    @Test
    void clearStartsTheNextFrameAtZero() {
        sections.add(ORIGIN_X, ORIGIN_Y, ORIGIN_Z);
        sections.clear();
        sections.add(SECOND_X, ORIGIN_Y, ORIGIN_Z);

        assertEquals(1, sections.count());
        assertEquals(SECOND_X, sections.buffer().asIntBuffer().get(0));
    }

    @Test
    void aZeroCapacityIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new CoverageSections(0));
    }
}
