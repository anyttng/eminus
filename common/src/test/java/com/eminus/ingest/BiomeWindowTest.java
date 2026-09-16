package com.eminus.ingest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.eminus.VanillaBootstrap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BiomeWindowTest {
    private static final long SEED = 0x5EED_1234_ABCDL;
    private static final int BIOMES = 5;
    private static final int[][] SECTIONS = {{0, 0, 0}, {-1, 4, -1}, {3, -2, -7}};

    private static final List<Holder<Biome>> HOLDERS = new ArrayList<>();

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
        for (int biome = 0; biome < BIOMES; biome++) {
            HOLDERS.add(Holder.direct(null));
        }
    }

    @Test
    void everyBlockOfTheSectionGetsTheBiomeTheGameGivesIt() {
        BiomeManager.NoiseBiomeSource world = (quartX, quartY, quartZ) ->
                HOLDERS.get(Math.floorMod(quartX * 3 + quartY * 5 + quartZ * 7, BIOMES));
        BiomeManager game = new BiomeManager(world, SEED);

        for (int[] section : SECTIONS) {
            BiomeWindow window = BiomeWindow.capture(world, section[0], section[1], section[2], SEED);
            assertFalse(window.uniform());

            for (int y = 0; y < SectionPyramid.SECTION_SIDE; y++) {
                for (int z = 0; z < SectionPyramid.SECTION_SIDE; z++) {
                    for (int x = 0; x < SectionPyramid.SECTION_SIDE; x++) {
                        assertSame(game.getBiome(new BlockPos(SectionPos.sectionToBlockCoord(section[0]) + x,
                                SectionPos.sectionToBlockCoord(section[1]) + y,
                                SectionPos.sectionToBlockCoord(section[2]) + z)), window.at(x, y, z));
                    }
                }
            }
        }
    }

    @Test
    void aWindowOfOneBiomeIsUniformAndAnswersIt() {
        Holder<Biome> only = HOLDERS.getFirst();
        BiomeWindow window = BiomeWindow.capture((quartX, quartY, quartZ) -> only, 2, 1, -3, SEED);

        assertTrue(window.uniform());
        assertSame(only, window.at(0, 0, 0));
        assertSame(only, window.at(SectionPyramid.SECTION_SIDE - 1, 7, 9));
    }
}
