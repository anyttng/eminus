package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.VanillaBootstrap;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SeedOverridesTest {
    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void aDoorsTopABedsFootAndATallPlantsTopSeedTheirOwnWay() {
        assertTrue(SeedOverrides.overridden(
                Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
        assertTrue(SeedOverrides.overridden(
                Blocks.STRAW_BED.defaultBlockState().setValue(BlockStateProperties.BED_PART, BedPart.FOOT)));
        assertTrue(SeedOverrides.overridden(
                Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER)));
    }

    @Test
    void aDoorsBottomStoneAndAFlowerSeedFromTheirPosition() {
        assertFalse(SeedOverrides.overridden(
                Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)));
        assertFalse(SeedOverrides.overridden(Blocks.STONE.defaultBlockState()));
        assertFalse(SeedOverrides.overridden(Blocks.POPPY.defaultBlockState()));
    }
}
