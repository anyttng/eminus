package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.eminus.VanillaBootstrap;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TintSweepTest {
    private static final int BIOMES = 3;
    private static final int VANILLA_ROWS = 4;
    private static final int SETS = 10;
    private static final int BIOME_STEP = 100;

    private static final Function<FluidState, @Nullable BlockTintSource> NO_FLUID_TINTS = fluid -> null;
    private static final UnaryOperator<BlockState> OWN_SHAPE = state -> state;

    private static List<Block> blocks;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
        blocks = BuiltInRegistries.BLOCK.stream().limit(SETS * (SETS + 1) / 2).toList();
    }

    @Test
    void vanillaColoursNeedFourRows() {
        BlockTintSource water = BlockTintSources.water();
        BiomeColours colours = colours();

        TintSweep.sweep(Block.BLOCK_STATE_REGISTRY, BlockColors.createDefault(),
                fluid -> fluid.getType().isSame(Fluids.WATER) ? water : null, OWN_SHAPE, colours);

        assertEquals(VANILLA_ROWS, colours.rowCount());
        assertTrue(colours.resolve(BlockTintSources.grassBlock(), Blocks.GRASS_BLOCK.defaultBlockState()).hasRow());
    }

    @Test
    void everySetKeepsARowAndTheSetUsedByTheMostBlocksTakesTheFirst() {
        BlockColors blockColors = new BlockColors();
        List<BlockTintSource> sources = new ArrayList<>();
        int next = 0;

        for (int set = 0; set < SETS; set++) {
            BlockTintSource source = varying(set);
            sources.add(source);
            List<Block> users = blocks.subList(next, next + set + 1);
            next += set + 1;
            blockColors.register(List.of(source), users.toArray(Block[]::new));
        }

        BiomeColours colours = colours();
        TintSweep.sweep(states(blocks), blockColors, NO_FLUID_TINTS, OWN_SHAPE, colours);

        assertEquals(SETS, colours.rowCount());
        assertEquals(Tint.row(0), colours.resolve(sources.get(SETS - 1), blocks.getLast().defaultBlockState()));
        assertEquals(Tint.row(SETS - 1), colours.resolve(sources.getFirst(), blocks.getFirst().defaultBlockState()));
    }

    @Test
    void theSameStatesSweptInAnotherOrderGiveTheSameRows() {
        BlockColors blockColors = new BlockColors();
        List<BlockTintSource> sources = new ArrayList<>();
        List<Block> users = blocks.subList(0, SETS);

        for (int set = 0; set < SETS; set++) {
            BlockTintSource source = varying(set);
            sources.add(source);
            blockColors.register(List.of(source), users.get(set));
        }

        BiomeColours forward = colours();
        BiomeColours backward = colours();
        TintSweep.sweep(states(users), blockColors, NO_FLUID_TINTS, OWN_SHAPE, forward);
        TintSweep.sweep(states(users.reversed()), blockColors, NO_FLUID_TINTS, OWN_SHAPE, backward);

        for (int set = 0; set < SETS; set++) {
            BlockState state = users.get(set).defaultBlockState();
            assertEquals(forward.resolve(sources.get(set), state), backward.resolve(sources.get(set), state));
        }
    }

    private static BiomeColours colours() {
        Map<String, BlockAndTintGetter> levels = new HashMap<>();
        for (int biome = 0; biome < BIOMES; biome++) {
            levels.put("test:biome_" + biome, new TintLevel(biome));
        }

        return new BiomeColours(levels, Set.of());
    }

    private static List<BlockState> states(List<Block> blocks) {
        return blocks.stream().map(Block::defaultBlockState).toList();
    }

    private static BlockTintSource varying(int set) {
        return new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return BiomeColours.NO_COLOUR;
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return ((TintLevel) level).biome() * BIOME_STEP + set;
            }
        };
    }
}
