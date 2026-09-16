package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.eminus.VanillaBootstrap;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BiomeColoursTest {
    private static final String PLAINS = "minecraft:plains";
    private static final String DESERT = "minecraft:desert";
    private static final int PLAINS_BIOME = 0;
    private static final int DESERT_BIOME = 1;
    private static final int GRASS_RESOLVER = 0;
    private static final int SPRUCE = 0xFF61_9961;
    private static final int RGB_MASK = 0x00FF_FFFF;
    private static final int UNPOWERED = 0;
    private static final int FULL_POWER = 15;

    private static BlockState stone;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
        stone = Blocks.STONE.defaultBlockState();
    }

    private final BiomeColours colours = new BiomeColours(
            Map.of(PLAINS, new TintLevel(PLAINS_BIOME), DESERT, new TintLevel(DESERT_BIOME)));

    @Test
    void aSourceThatVariesByBiomeResolvesToItsRowAndHoldsOneColourPerBiome() {
        BlockTintSource grass = BlockTintSources.grass();
        colours.assign(List.of(colours.sample(grass, stone)));

        assertEquals(Tint.row(0), colours.resolve(grass, stone));
        assertEquals(TintLevel.colour(PLAINS_BIOME, GRASS_RESOLVER), colours.colour(0, PLAINS));
        assertEquals(TintLevel.colour(DESERT_BIOME, GRASS_RESOLVER), colours.colour(0, DESERT));
        assertEquals(BiomeColours.NO_COLOUR, colours.colour(0, "minecraft:nether_wastes"));
    }

    @Test
    void twoInstancesWithTheSameColoursShareOneRow() {
        BlockTintSource grass = BlockTintSources.grass();
        BlockTintSource grassBlock = BlockTintSources.grassBlock();
        colours.assign(List.of(colours.sample(grass, stone)));

        assertEquals(colours.sample(grass, stone), colours.sample(grassBlock, stone));
        assertEquals(Tint.row(0), colours.resolve(grassBlock, stone));
    }

    @Test
    void aSourceTheSameInEveryBiomeResolvesToAConstantWithNoRow() {
        Tint tint = colours.resolve(BlockTintSources.constant(SPRUCE), stone);

        assertEquals(Tint.constant(SPRUCE & RGB_MASK), tint);
        assertFalse(tint.hasRow());
    }

    @Test
    void aStateDependentSourceResolvesPerState() {
        BlockTintSource redstone = BlockTintSources.redstone();
        BlockState unpowered = Blocks.REDSTONE_WIRE.defaultBlockState().setValue(RedStoneWireBlock.POWER, UNPOWERED);
        BlockState powered = unpowered.setValue(RedStoneWireBlock.POWER, FULL_POWER);

        assertNotEquals(colours.resolve(redstone, unpowered), colours.resolve(redstone, powered));
    }

    @Test
    void aVaryingSourceWithoutARowDrawsUntinted() {
        assertEquals(Tint.UNTINTED, colours.resolve(BlockTintSources.foliage(), stone));
    }

    @Test
    void noSourceMeansNoTint() {
        assertNull(colours.resolve(null, stone));
    }

    @Test
    void moreRowsThanTheTableHoldsAreRefused() {
        List<BiomeColours.Colours> ranked =
                Collections.nCopies(BiomeColours.MAX_ROWS + 1, colours.sample(BlockTintSources.grass(), stone));

        assertThrows(IllegalArgumentException.class, () -> colours.assign(ranked));
    }
}
