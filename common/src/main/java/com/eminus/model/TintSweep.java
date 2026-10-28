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
            BiomeColors colors) {
        colors.assign(ranked(users(states, blockTints, fluidTints, shapes, colors)));
    }

    private static Map<BiomeColors.Colors, Set<Block>> users(Iterable<BlockState> states, BlockTints blockTints,
            Function<FluidState, @Nullable TintSource> fluidTints, UnaryOperator<BlockState> shapes,
            BiomeColors colors) {
        Map<BiomeColors.Colors, Set<Block>> users = new HashMap<>();
        for (BlockState state : states) {
            BlockState shape = shapes.apply(state);
            for (TintSource tint : blockTints.sources(shape)) {
                count(users, colors, tint, shape, state.getBlock());
            }

            FluidState fluid = state.getFluidState();
            TintSource fluidTint = fluid.isEmpty() ? null : fluidTints.apply(fluid);
            if (fluidTint != null) {
                count(users, colors, fluidTint, state, state.getBlock());
            }
        }

        return users;
    }

    private static List<BiomeColors.Colors> ranked(Map<BiomeColors.Colors, Set<Block>> users) {
        return users.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<BiomeColors.Colors, Set<Block>> user) ->
                        user.getValue().size()).reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static void count(Map<BiomeColors.Colors, Set<Block>> users, BiomeColors colors,
            TintSource tint, BlockState state, Block block) {
        BiomeColors.Colors sampled;
        try {
            sampled = colors.sample(tint, state);
        } catch (RuntimeException failure) {
            return;
        }

        if (sampled.values().length > 0 && !sampled.blank()) {
            users.computeIfAbsent(sampled, set -> new HashSet<>()).add(block);
        }
    }
}
