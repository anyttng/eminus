package com.eminus.ingest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;

class LightDigestsTest {
    private static final int CHUNK_X = 3;
    private static final int CHUNK_Z = -2;
    private static final int MIN_SECTION_Y = -4;
    private static final int MAX_SECTION_Y = 19;
    private static final int LIGHT = 7;

    private final LightDigests digests = new LightDigests();
    private final DataLayer layer = new DataLayer(LIGHT);

    @Test
    void forgettingAColumnDropsTheSkyDigestOfEverySectionInRange() {
        for (int sectionY = MIN_SECTION_Y; sectionY <= MAX_SECTION_Y; sectionY++) {
            digests.record(node(CHUNK_X, sectionY, CHUNK_Z), layer, null);
        }

        digests.forgetColumn(CHUNK_X, CHUNK_Z, MIN_SECTION_Y, MAX_SECTION_Y);

        for (int sectionY = MIN_SECTION_Y; sectionY <= MAX_SECTION_Y; sectionY++) {
            assertTrue(digests.record(node(CHUNK_X, sectionY, CHUNK_Z), layer, null), "section y " + sectionY);
        }
    }

    @Test
    void forgettingAColumnDropsTheBlockDigestOfEverySectionInRange() {
        for (int sectionY = MIN_SECTION_Y; sectionY <= MAX_SECTION_Y; sectionY++) {
            digests.record(node(CHUNK_X, sectionY, CHUNK_Z), null, layer);
        }

        digests.forgetColumn(CHUNK_X, CHUNK_Z, MIN_SECTION_Y, MAX_SECTION_Y);

        for (int sectionY = MIN_SECTION_Y; sectionY <= MAX_SECTION_Y; sectionY++) {
            assertTrue(digests.record(node(CHUNK_X, sectionY, CHUNK_Z), null, layer), "section y " + sectionY);
        }
    }

    @Test
    void forgettingAColumnKeepsItsNeighborAndTheSectionsPastItsRange() {
        long neighbor = node(CHUNK_X + 1, MIN_SECTION_Y, CHUNK_Z);
        long above = node(CHUNK_X, MAX_SECTION_Y + 1, CHUNK_Z);
        long below = node(CHUNK_X, MIN_SECTION_Y - 1, CHUNK_Z);
        digests.record(neighbor, layer, layer);
        digests.record(above, layer, layer);
        digests.record(below, layer, layer);

        digests.forgetColumn(CHUNK_X, CHUNK_Z, MIN_SECTION_Y, MAX_SECTION_Y);

        assertFalse(digests.record(neighbor, layer, layer));
        assertFalse(digests.record(above, layer, layer));
        assertFalse(digests.record(below, layer, layer));
    }

    private static long node(int chunkX, int sectionY, int chunkZ) {
        return SectionPos.asLong(chunkX, sectionY, chunkZ);
    }
}
