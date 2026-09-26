package com.eminus.model;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.eminus.model.port.BlockTints;
import com.eminus.model.port.TintSource;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import org.jspecify.annotations.Nullable;

public final class TintSweep {
    private TintSweep() {
    }

    public static void sweep(Iterable<BlockState> states, BlockTints blockTints,
            Function<FluidState, @Nullable TintSource> fluidTints, UnaryOperator<BlockState> shapes,
            BiomeColours colours) {
        Map<BiomeColours.Colours, Set<Block>> users = new HashMap<>();

        for (BlockState state : states) {
            BlockState shape = shapes.apply(state);
            for (TintSource tint : blockTints.sources(shape)) {
                count(users, colours, tint, shape, state.getBlock());
            }

            FluidState fluid = state.getFluidState();
            TintSource fluidTint = fluid.isEmpty() ? null : fluidTints.apply(fluid);
            if (fluidTint != null) {
                count(users, colours, fluidTint, state, state.getBlock());
            }
        }

        List<BiomeColours.Colours> ranked = users.keySet().stream()
                .sorted(Comparator.comparingInt((BiomeColours.Colours set) -> users.get(set).size()).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        colours.assign(ranked);
    }

    private static void count(Map<BiomeColours.Colours, Set<Block>> users, BiomeColours colours,
            TintSource tint, BlockState state, Block block) {
        BiomeColours.Colours sampled;
        try {
            sampled = colours.sample(tint, state);
        } catch (RuntimeException failure) {
            return;
        }

        if (sampled.values().length > 0 && !sampled.uniform()) {
            users.computeIfAbsent(sampled, set -> new HashSet<>()).add(block);
        }
    }
}
