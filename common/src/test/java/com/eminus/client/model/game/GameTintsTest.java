package com.eminus.client.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.eminus.VanillaBootstrap;
import com.eminus.model.BiomeColors;
import com.eminus.model.Tint;
import com.eminus.model.TintSweep;
import com.eminus.model.port.TintBiome;
import com.eminus.model.port.TintSource;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GameTintsTest {
    private static final int FIRST_BIOME = 0;
    private static final int SECOND_BIOME = 1;
    private static final int TWO_BIOMES = 2;
    private static final int THREE_BIOMES = 3;
    private static final int GRASS_RESOLVER = 0;
    private static final int VANILLA_ROWS = 31;
    private static final int SPRUCE_LEAVES = -10380959;
    private static final int FIRST_LAYER = 0;
    private static final int SAMPLE_COLUMN = 0;
    private static final int RGB_MASK = 0x00FF_FFFF;
    private static final int UNPOWERED = 0;
    private static final int FULL_POWER = 15;

    private static BlockState stone;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
        stone = Blocks.STONE.defaultBlockState();
    }

    @Test
    void aVanillaSourceAnswersWithTheBiomeOfTheLevelItIsHanded() {
        TintSource grass = new GameTintSource(BlockTintSources.grass());

        for (int biome : new int[] {FIRST_BIOME, SECOND_BIOME}) {
            assertEquals(TintLevel.color(biome, GRASS_RESOLVER),
                    grass.color(stone, new TintLevel(biome), SAMPLE_COLUMN, SAMPLE_COLUMN) & RGB_MASK);
        }
    }

    @Test
    void twoVanillaInstancesWithTheSameColorsShareOneRow() {
        BiomeColors colors = colors(TWO_BIOMES);
        TintSource grass = new GameTintSource(BlockTintSources.grass());
        TintSource grassBlock = new GameTintSource(BlockTintSources.grassBlock());
        colors.assign(List.of(colors.sample(grass, stone)));

        assertEquals(Tint.row(0), colors.resolve(grassBlock, stone));
    }

    @Test
    void aStateDependentVanillaSourceResolvesPerState() {
        BiomeColors colors = colors(TWO_BIOMES);
        TintSource redstone = new GameTintSource(BlockTintSources.redstone());
        BlockState unpowered = Blocks.REDSTONE_WIRE.defaultBlockState().setValue(RedStoneWireBlock.POWER, UNPOWERED);
        BlockState powered = unpowered.setValue(RedStoneWireBlock.POWER, FULL_POWER);
        colors.assign(List.of(colors.sample(redstone, unpowered), colors.sample(redstone, powered)));

        assertNotEquals(colors.resolve(redstone, unpowered), colors.resolve(redstone, powered));
    }

    @Test
    void vanillaColorsNeedARowPerBiomeSetAndPerConstant() {
        GameTints tints = new GameTints(BlockColors.createDefault());
        TintSource water = new GameTintSource(BlockTintSources.water());
        BiomeColors colors = colors(THREE_BIOMES);

        TintSweep.sweep(Block.BLOCK_STATE_REGISTRY, tints,
                fluid -> fluid.getType().isSame(Fluids.WATER) ? water : null, state -> state, colors);

        assertEquals(VANILLA_ROWS, colors.rowCount());
        BlockState grassBlock = Blocks.GRASS_BLOCK.defaultBlockState();
        assertTrue(colors.resolve(tints.source(grassBlock, FIRST_LAYER), grassBlock).hasRow());

        BlockState spruce = Blocks.SPRUCE_LEAVES.defaultBlockState();
        Tint spruceTint = colors.resolve(tints.source(spruce, FIRST_LAYER), spruce);
        assertTrue(spruceTint.hasRow());
        assertEquals(SPRUCE_LEAVES & RGB_MASK, colors.constant(spruceTint.row()));
    }

    @Test
    void aStateWithoutAVanillaSourceHasNone() {
        GameTints tints = new GameTints(BlockColors.createDefault());

        assertNull(tints.source(stone, FIRST_LAYER));
        assertTrue(tints.sources(stone).isEmpty());
    }

    private static BiomeColors colors(int count) {
        List<TintBiome> biomes = new ArrayList<>();
        for (int biome = 0; biome < count; biome++) {
            biomes.add(new TintLevel(biome));
        }

        return new BiomeColors(biomes);
    }
}
