package com.eminus.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.world.level.block.entity.BeaconBeamOwner;

import org.junit.jupiter.api.Test;

class BeamWatchTest {
    private static final long LEVEL_TICK = 4L * BeamWatch.LEVEL_PERIOD_TICKS;
    private static final int RED = 0xFFB02E26;

    @Test
    void aBeaconIsNotSettledBeforeItsFirstScanEnds() {
        BeamWatch watch = new BeamWatch();

        assertFalse(watch.settles(LEVEL_TICK, false));
    }

    @Test
    void aScanEndingOnALevelTickSettlesOnlyAtTheNextLevelTick() {
        BeamWatch watch = new BeamWatch();

        assertFalse(watch.settles(LEVEL_TICK, true));
        assertFalse(watch.settles(LEVEL_TICK + 1, false));
        assertTrue(watch.settles(LEVEL_TICK + BeamWatch.LEVEL_PERIOD_TICKS, false));
    }

    @Test
    void aSettledBeaconStaysSettled() {
        BeamWatch watch = new BeamWatch();
        watch.settles(LEVEL_TICK - 1, true);
        watch.settles(LEVEL_TICK, false);

        assertTrue(watch.settles(LEVEL_TICK + 1, false));
    }

    @Test
    void anUnsettledBeaconWaitsForAFreshScanAndLevelTick() {
        BeamWatch watch = new BeamWatch();
        watch.settles(LEVEL_TICK - 1, true);
        watch.settles(LEVEL_TICK, false);

        watch.unsettle();
        assertFalse(watch.settles(LEVEL_TICK + BeamWatch.LEVEL_PERIOD_TICKS, false));
        assertFalse(watch.settles(LEVEL_TICK + BeamWatch.LEVEL_PERIOD_TICKS + 1, true));
        assertTrue(watch.settles(LEVEL_TICK + 2L * BeamWatch.LEVEL_PERIOD_TICKS, false));
    }

    @Test
    void theFirstReadingIsReportedEvenWhenEmpty() {
        BeamWatch watch = new BeamWatch();

        assertEquals(List.of(), watch.changed(List.of()));
    }

    @Test
    void aRepeatedReadingIsNotReported() {
        BeamWatch watch = new BeamWatch();
        watch.changed(List.of(section(RED, 3)));

        assertNull(watch.changed(List.of(section(RED, 3))));
    }

    @Test
    void aTallerSectionIsReported() {
        BeamWatch watch = new BeamWatch();
        watch.changed(List.of(section(RED, 3)));

        assertEquals(List.of(new BeamSection(RED, 4)), watch.changed(List.of(section(RED, 4))));
    }

    private static BeaconBeamOwner.Section section(int color, int height) {
        BeaconBeamOwner.Section section = new BeaconBeamOwner.Section(color);
        for (int grown = 1; grown < height; grown++) {
            section.increaseHeight();
        }

        return section;
    }
}
