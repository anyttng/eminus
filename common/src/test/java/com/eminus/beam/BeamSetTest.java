package com.eminus.beam;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

class BeamSetTest {
    private static final long BEACON = BlockPos.asLong(33, 70, -17);
    private static final long NEIGHBOR = BlockPos.asLong(47, 70, -2);
    private static final long ELSEWHERE = BlockPos.asLong(48, 70, -17);
    private static final List<BeamSection> RED = List.of(new BeamSection(0xFFB02E26, 5));
    private static final List<BeamSection> BLUE = List.of(new BeamSection(0xFF3C44AA, 5));

    private final RecordingStore store = new RecordingStore();
    private final List<Runnable> queued = new ArrayList<>();

    @Test
    void aPutBeamIsWrittenToTheStore() {
        BeamSet beams = new BeamSet(store, Runnable::run);

        beams.put(BEACON, RED);

        assertTrue(beams.holds(BEACON));
        assertArrayEquals(BeamRecord.encode(RED), store.rows.get(BEACON));
    }

    @Test
    void anUnchangedBeamIsNotWrittenAgainAndKeepsTheVersion() {
        BeamSet beams = new BeamSet(store, Runnable::run);
        beams.put(BEACON, RED);
        long version = beams.version();

        beams.put(BEACON, List.copyOf(RED));

        assertEquals(1, store.writes);
        assertEquals(version, beams.version());
    }

    @Test
    void emptySectionsRemoveTheBeamFromTheSetAndTheStore() {
        BeamSet beams = new BeamSet(store, Runnable::run);
        beams.put(BEACON, RED);

        beams.put(BEACON, List.of());

        assertFalse(beams.holds(BEACON));
        assertFalse(store.rows.containsKey(BEACON));
    }

    @Test
    void aWriteRunLateStoresTheNewestSections() {
        BeamSet beams = new BeamSet(store, queued::add);
        beams.put(BEACON, RED);
        beams.put(BEACON, BLUE);

        queued.forEach(Runnable::run);

        assertArrayEquals(BeamRecord.encode(BLUE), store.rows.get(BEACON));
    }

    @Test
    void flushWritesWhatTheQueueHasNotRunYet() {
        BeamSet beams = new BeamSet(store, queued::add);
        beams.put(BEACON, RED);

        beams.flush();

        assertArrayEquals(BeamRecord.encode(RED), store.rows.get(BEACON));
    }

    @Test
    void loadedBeamsAreHeldAndNotWrittenBack() {
        BeamSet beams = new BeamSet(store, Runnable::run);

        beams.load(BEACON, BeamRecord.encode(RED));

        assertEquals(List.of(new Beam(BEACON, RED)), beams.snapshot().beams());
        assertEquals(0, store.writes);
    }

    @Test
    void aDamagedRecordIsSkippedOnLoad() {
        BeamSet beams = new BeamSet(store, Runnable::run);

        beams.load(BEACON, new byte[] {1, 2, 3});

        assertFalse(beams.holds(BEACON));
    }

    @Test
    void inChunkListsOnlyTheBeamsOfThatChunk() {
        BeamSet beams = new BeamSet(store, Runnable::run);
        beams.put(BEACON, RED);
        beams.put(NEIGHBOR, RED);
        beams.put(ELSEWHERE, RED);

        assertEquals(List.of(BEACON), List.copyOf(beams.inChunk(2, -2)));
    }

    @Test
    void everyChangeMovesTheVersion() {
        BeamSet beams = new BeamSet(store, Runnable::run);
        long empty = beams.version();
        beams.put(BEACON, RED);
        long put = beams.version();
        beams.remove(BEACON);

        assertNotEquals(empty, put);
        assertNotEquals(put, beams.version());
    }

    private static final class RecordingStore implements BeamStore {
        private final Map<Long, byte[]> rows = new HashMap<>();
        private int writes;

        @Override
        public void putBeam(long pos, byte[] sections) {
            writes++;
            rows.put(pos, sections);
        }

        @Override
        public void deleteBeam(long pos) {
            writes++;
            rows.remove(pos);
        }

        @Override
        public void readBeams(Rows into) {
            rows.forEach(into::row);
        }
    }
}
