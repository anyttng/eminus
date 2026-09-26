package com.eminus.client.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.VanillaBootstrap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BakeLevelTest {
    private static final BlockPos SAMPLE = new BlockPos(3, 4, 5);
    private static final int COLUMN = 100;
    private static final String NAME = "test:biome";

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void aTintResolverIsAnsweredWithTheLevelsOwnBiomeAndPosition() {
        BakeLevel level = new BakeLevel(null, NAME, false);

        int colour = level.getBlockTint(SAMPLE, (biome, x, z) -> {
            assertNull(biome);
            return (int) x * COLUMN + (int) z;
        });

        assertEquals(SAMPLE.getX() * COLUMN + SAMPLE.getZ(), colour);
    }

    @Test
    void theNeighbourhoodIsEmpty() {
        BakeLevel level = new BakeLevel(null, NAME, false);

        assertEquals(Blocks.AIR.defaultBlockState(), level.getBlockState(SAMPLE));
        assertTrue(level.getFluidState(SAMPLE).isEmpty());
        assertNull(level.getBlockEntity(SAMPLE));
    }
}
