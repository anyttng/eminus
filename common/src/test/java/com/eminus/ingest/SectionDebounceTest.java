package com.eminus.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

class SectionDebounceTest {
    private static final long WINDOW = 500L;
    private static final long SECTION = 42L;
    private static final long OTHER_SECTION = 43L;

    private final SectionDebounce debounce = new SectionDebounce(WINDOW);

    @Test
    void aSectionWaitsOutTheWindow() {
        debounce.mark(SECTION, 1000L);

        assertEquals(LongList.of(), drain(1000L + WINDOW - 1));
        assertEquals(LongList.of(SECTION), drain(1000L + WINDOW));
    }

    @Test
    void aBurstInOneSectionGivesOneIngest() {
        for (long now = 1000L; now <= 2000L; now += 100L) {
            debounce.mark(SECTION, now);
            assertTrue(drain(now).isEmpty());
        }

        assertEquals(LongList.of(SECTION), drain(2000L + WINDOW));
        assertEquals(LongList.of(), drain(9000L));
    }

    @Test
    void sectionsAreHeldApart() {
        debounce.mark(SECTION, 1000L);
        debounce.mark(OTHER_SECTION, 1400L);

        assertEquals(LongList.of(SECTION), drain(1500L));
        assertEquals(LongList.of(OTHER_SECTION), drain(1900L));
    }

    private LongList drain(long now) {
        LongArrayList ready = new LongArrayList();
        debounce.drain(now, ready::add);
        return ready;
    }
}
