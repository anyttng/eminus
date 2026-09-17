package com.eminus.ingest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import com.eminus.cell.VoxelEntry;

import net.minecraft.world.level.chunk.DataLayer;

import org.junit.jupiter.api.Test;

class SectionConverterTest {
    private static final int SIDE = SectionPyramid.SECTION_SIDE;
    private static final int LAST = SIDE - 1;
    private static final int TORCH_SPILL = 11;
    private static final int SHADED_SKY = VoxelEntry.MAX_LIGHT - 1;
    private static final byte FULL_NIBBLES = (byte) 0xFF;

    @Test
    void noLayersMatchTheBlankEntry() {
        assertFalse(SectionConverter.lightDiffersFromBlank(null, null));
    }

    @Test
    void openSkyAndNoBlockLightMatchTheBlankEntry() {
        assertFalse(SectionConverter.lightDiffersFromBlank(new DataLayer(VoxelEntry.MAX_LIGHT), new DataLayer()));
    }

    @Test
    void aSkyLayerStoredFullMatchesTheBlankEntry() {
        byte[] data = new byte[DataLayer.SIZE];
        Arrays.fill(data, FULL_NIBBLES);

        assertFalse(SectionConverter.lightDiffersFromBlank(new DataLayer(data), null));
    }

    @Test
    void oneBlockLitVoxelDiffers() {
        DataLayer blockLight = new DataLayer();
        blockLight.set(LAST, 0, LAST, TORCH_SPILL);

        assertTrue(SectionConverter.lightDiffersFromBlank(null, blockLight));
    }

    @Test
    void oneShadedSkyVoxelDiffers() {
        DataLayer skyLight = new DataLayer(VoxelEntry.MAX_LIGHT);
        skyLight.set(0, LAST, 0, SHADED_SKY);

        assertTrue(SectionConverter.lightDiffersFromBlank(skyLight, null));
    }

    @Test
    void aDarkSkyLayerDiffers() {
        assertTrue(SectionConverter.lightDiffersFromBlank(new DataLayer(), null));
    }

    @Test
    void aBlockLayerStoredEmptyMatchesTheBlankEntry() {
        assertFalse(SectionConverter.lightDiffersFromBlank(null, new DataLayer(new byte[DataLayer.SIZE])));
    }
}
