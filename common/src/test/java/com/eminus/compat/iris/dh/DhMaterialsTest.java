package com.eminus.compat.iris.dh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eminus.VanillaBootstrap;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DhMaterialsTest {
    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void fluidsSplitByTheirLight() {
        assertClass(DhMaterials.WATER, Blocks.WATER);
        assertClass(DhMaterials.LAVA, Blocks.LAVA);
    }

    @Test
    void anEmittingBlockIsIlluminated() {
        assertClass(DhMaterials.ILLUMINATED, Blocks.GLOWSTONE);
    }

    @Test
    void airAndLeavesKeepTheirOwnClasses() {
        assertClass(DhMaterials.AIR, Blocks.AIR);
        assertClass(DhMaterials.LEAVES, Blocks.OAK_LEAVES);
    }

    @Test
    void groundBlocksClassifyByTheirMapColour() {
        assertClass(DhMaterials.GRASS, Blocks.GRASS_BLOCK);
        assertClass(DhMaterials.DIRT, Blocks.DIRT);
        assertClass(DhMaterials.STONE, Blocks.STONE);
        assertClass(DhMaterials.DEEPSLATE, Blocks.DEEPSLATE);
        assertClass(DhMaterials.NETHER_STONE, Blocks.NETHERRACK);
        assertClass(DhMaterials.SAND, Blocks.SAND);
        assertClass(DhMaterials.SNOW, Blocks.SNOW_BLOCK);
        assertClass(DhMaterials.TERRACOTTA, Blocks.DYED_TERRACOTTA.white());
        assertClass(DhMaterials.METAL, Blocks.IRON_BLOCK);
    }

    @Test
    void woodSoundsBeforeAMapColourBorrowedFromAnotherMaterial() {
        assertClass(DhMaterials.WOOD, Blocks.CHERRY_PLANKS);
    }

    @Test
    void aBlockWhoseMapColourSaysNothingFallsBackToItsSound() {
        assertClass(DhMaterials.WOOD, Blocks.OAK_LOG);
        assertClass(DhMaterials.SAND, Blocks.RED_SAND);
    }

    @Test
    void aBlockNeitherNamesIsUnknown() {
        assertClass(DhMaterials.UNKNOWN, Blocks.GLASS);
    }

    private static void assertClass(int expected, Block block) {
        assertEquals(expected, DhMaterials.CLASSES.applyAsInt(block.defaultBlockState()), block.toString());
    }
}
