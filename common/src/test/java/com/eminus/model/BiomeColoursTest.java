package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import com.eminus.VanillaBootstrap;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BiomeColoursTest {
    private static final String PLAINS = "minecraft:plains";
    private static final String DESERT = "minecraft:desert";
    private static final int PLAINS_COLOUR = 0x0091_BD59;
    private static final int DESERT_COLOUR = 0x00BF_B755;

    private static final BlockTintSource FROM_LEVEL = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return BiomeColours.NO_COLOUR;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return level.getBlockTint(pos, (biome, x, z) -> BiomeColours.NO_COLOUR);
        }
    };

    private static final BlockTintSource UNFILLED = state -> BiomeColours.NO_COLOUR;

    private static BlockState stone;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
        stone = Blocks.STONE.defaultBlockState();
    }

    private final BiomeColours colours = new BiomeColours(
            Map.of(PLAINS, new FixedTint(PLAINS_COLOUR), DESERT, new FixedTint(DESERT_COLOUR)));

    @Test
    void aFilledTintHoldsOneColourPerKnownBiome() {
        colours.fill(FROM_LEVEL, stone);

        assertEquals(2, colours.biomeCount());
        assertEquals(PLAINS_COLOUR, colours.colour(FROM_LEVEL, PLAINS));
        assertEquals(DESERT_COLOUR, colours.colour(FROM_LEVEL, DESERT));
    }

    @Test
    void aTintNobodyFilledHasNoColour() {
        colours.fill(FROM_LEVEL, stone);

        assertEquals(BiomeColours.NO_COLOUR, colours.colour(UNFILLED, PLAINS));
        assertEquals(BiomeColours.NO_COLOUR, colours.colour(FROM_LEVEL, "minecraft:nether_wastes"));
    }

    @Test
    void fillingTheSameTintTwiceKeepsOneRow() {
        colours.fill(FROM_LEVEL, stone);
        colours.fill(FROM_LEVEL, stone);

        assertEquals(1, colours.tintCount());
    }

    private record FixedTint(int colour) implements BlockAndTintGetter {
        @Override
        public int getBlockTint(BlockPos pos, ColorResolver color) {
            return colour;
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return CardinalLighting.DEFAULT;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return LevelLightEngine.EMPTY;
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 0;
        }

        @Override
        public int getMinY() {
            return 0;
        }
    }
}
