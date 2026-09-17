package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.VanillaBootstrap;

import net.minecraft.world.level.block.Blocks;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SeedOverridesTest {
    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void aDoorABedAndATallPlantSeedTheirOwnWay() {
        assertTrue(SeedOverrides.overridden(Blocks.OAK_DOOR));
        assertTrue(SeedOverrides.overridden(Blocks.BED.white()));
        assertTrue(SeedOverrides.overridden(Blocks.TALL_GRASS));
    }

    @Test
    void stoneAndGrassSeedFromTheirPosition() {
        assertFalse(SeedOverrides.overridden(Blocks.STONE));
        assertFalse(SeedOverrides.overridden(Blocks.GRASS_BLOCK));
    }
}
