package com.eminus.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.Dictionary;
import com.eminus.cell.StateTable;
import com.eminus.cell.VoxelEntry;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SectionConverterTest {
    private static final int SIDE = SectionPyramid.SECTION_SIDE;
    private static final int LAST = SIDE - 1;
    private static final int TORCH_SPILL = 11;
    private static final int SHADED_SKY = VoxelEntry.MAX_LIGHT - 1;
    private static final byte FULL_NIBBLES = (byte) 0xFF;
    private static final long SEED = 0x5EED_1234_ABCDL;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void aBlockWhoseBiomeLiesInAMissingChunkKeepsItsStoredBiome() {
        Holder<Biome> loaded = Holder.direct(null);
        BiomeManager.NoiseBiomeSource world = (quartX, quartY, quartZ) ->
                quartX < QuartPos.fromSection(1) ? loaded : null;
        BiomeWindow window = BiomeWindow.capture(world, 0, 0, 0, SEED);
        SectionPyramid pyramid = new SectionPyramid();
        StateTable states = new StateTable(new Dictionary<>((id, value) -> { }));
        Dictionary<String> biomes = new Dictionary<>((id, value) -> { });

        SectionConverter.convert(airSection(loaded), window, null, null, states, biomes, pyramid);

        long[] level = pyramid.level(DetailLevel.MIN);
        int kept = 0;
        for (int y = 0; y < SIDE; y++) {
            for (int z = 0; z < SIDE; z++) {
                for (int x = 0; x < SIDE; x++) {
                    boolean keeps = VoxelEntry.biome(level[SectionPyramid.indexAt(SIDE, x, y, z)])
                            == VoxelEntry.KEPT_BIOME;
                    assertEquals(window.at(x, y, z) == null, keeps);
                    if (keeps) {
                        kept++;
                    }
                }
            }
        }

        assertTrue(kept > 0);
        assertTrue(kept < SIDE * SIDE * SIDE);
    }

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

    private static LevelChunkSection airSection(Holder<Biome> biome) {
        CrudeIncrementalIntIdentityHashBiMap<Holder<Biome>> biomeIds = CrudeIncrementalIntIdentityHashBiMap.create(1);
        biomeIds.add(biome);
        return new LevelChunkSection(
                new PalettedContainer<>(Blocks.AIR.defaultBlockState(),
                        Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),
                new PalettedContainer<>(biome, Strategy.createForBiomes(biomeIds)));
    }
}
