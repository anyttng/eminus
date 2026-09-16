package com.eminus.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoxelEntryTest {
    @Test
    void everyFieldRoundTrips() {
        long entry = VoxelEntry.pack(123456, 4321, VoxelEntry.light(12, 7));

        assertEquals(123456, VoxelEntry.state(entry));
        assertEquals(4321, VoxelEntry.biome(entry));
        assertEquals(12, VoxelEntry.skyLight(entry));
        assertEquals(7, VoxelEntry.blockLight(entry));
    }

    @Test
    void fieldsAtTheirMaximumDoNotBleedIntoEachOther() {
        long entry = VoxelEntry.pack(Integer.MAX_VALUE, VoxelEntry.MAX_BIOME_ID,
                VoxelEntry.light(VoxelEntry.MAX_LIGHT, VoxelEntry.MAX_LIGHT));

        assertEquals(Integer.MAX_VALUE, VoxelEntry.state(entry));
        assertEquals(VoxelEntry.MAX_BIOME_ID, VoxelEntry.biome(entry));
        assertEquals(VoxelEntry.MAX_LIGHT, VoxelEntry.skyLight(entry));
        assertEquals(VoxelEntry.MAX_LIGHT, VoxelEntry.blockLight(entry));
    }

    @Test
    void everyLightPairRoundTrips() {
        for (int sky = 0; sky <= VoxelEntry.MAX_LIGHT; sky++) {
            for (int block = 0; block <= VoxelEntry.MAX_LIGHT; block++) {
                long entry = VoxelEntry.pack(1, 1, VoxelEntry.light(sky, block));

                assertEquals(sky, VoxelEntry.skyLight(entry));
                assertEquals(block, VoxelEntry.blockLight(entry));
            }
        }
    }

    @Test
    void airHasStateZeroAndFullSkyLight() {
        assertEquals(VoxelEntry.AIR_STATE_ID, VoxelEntry.state(VoxelEntry.AIR));
        assertEquals(VoxelEntry.MAX_LIGHT, VoxelEntry.skyLight(VoxelEntry.AIR));
        assertEquals(0, VoxelEntry.blockLight(VoxelEntry.AIR));
        assertTrue(VoxelEntry.isAir(VoxelEntry.AIR));
        assertFalse(VoxelEntry.isAir(VoxelEntry.pack(1, 0, 0)));
    }
}
