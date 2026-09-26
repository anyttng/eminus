package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.eminus.VanillaBootstrap;
import com.eminus.model.port.BlockTints;
import com.eminus.model.port.TintBiome;
import com.eminus.model.port.TintSource;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TintSweepTest {
    private static final int BIOMES = 3;
    private static final int SETS = 10;
    private static final int BIOME_STEP = 100;

    private static final Function<FluidState, @Nullable TintSource> NO_FLUID_TINTS = fluid -> null;
    private static final UnaryOperator<BlockState> OWN_SHAPE = state -> state;

    private static List<Block> blocks;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
        blocks = BuiltInRegistries.BLOCK.stream().limit(SETS * (SETS + 1) / 2).toList();
    }

    @Test
    void everySetKeepsARowAndTheSetUsedByTheMostBlocksTakesTheFirst() {
        Map<Block, TintSource> byBlock = new HashMap<>();
        List<TintSource> sources = new ArrayList<>();
        int next = 0;

        for (int set = 0; set < SETS; set++) {
            TintSource source = varying(set);
            sources.add(source);
            for (Block user : blocks.subList(next, next + set + 1)) {
                byBlock.put(user, source);
            }

            next += set + 1;
        }

        BiomeColours colours = colours();
        TintSweep.sweep(states(blocks), tints(byBlock), NO_FLUID_TINTS, OWN_SHAPE, colours);

        assertEquals(SETS, colours.rowCount());
        assertEquals(Tint.row(0), colours.resolve(sources.get(SETS - 1), blocks.getLast().defaultBlockState()));
        assertEquals(Tint.row(SETS - 1), colours.resolve(sources.getFirst(), blocks.getFirst().defaultBlockState()));
    }

    @Test
    void theSameStatesSweptInAnotherOrderGiveTheSameRows() {
        Map<Block, TintSource> byBlock = new HashMap<>();
        List<TintSource> sources = new ArrayList<>();
        List<Block> users = blocks.subList(0, SETS);

        for (int set = 0; set < SETS; set++) {
            TintSource source = varying(set);
            sources.add(source);
            byBlock.put(users.get(set), source);
        }

        BiomeColours forward = colours();
        BiomeColours backward = colours();
        TintSweep.sweep(states(users), tints(byBlock), NO_FLUID_TINTS, OWN_SHAPE, forward);
        TintSweep.sweep(states(users.reversed()), tints(byBlock), NO_FLUID_TINTS, OWN_SHAPE, backward);

        for (int set = 0; set < SETS; set++) {
            BlockState state = users.get(set).defaultBlockState();
            assertEquals(forward.resolve(sources.get(set), state), backward.resolve(sources.get(set), state));
        }
    }

    private static BiomeColours colours() {
        List<TintBiome> biomes = new ArrayList<>();
        for (int biome = 0; biome < BIOMES; biome++) {
            biomes.add(new FakeBiome(biome));
        }

        return new BiomeColours(biomes);
    }

    private static BlockTints tints(Map<Block, TintSource> byBlock) {
        return new BlockTints() {
            @Override
            public List<TintSource> sources(BlockState state) {
                TintSource source = byBlock.get(state.getBlock());
                return source == null ? List.of() : List.of(source);
            }

            @Override
            public @Nullable TintSource source(BlockState state, int layer) {
                return byBlock.get(state.getBlock());
            }
        };
    }

    private static List<BlockState> states(List<Block> blocks) {
        return blocks.stream().map(Block::defaultBlockState).toList();
    }

    private static TintSource varying(int set) {
        return (state, biome, x, z) -> ((FakeBiome) biome).index() * BIOME_STEP + set;
    }
}
