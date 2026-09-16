package com.eminus.model;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import com.eminus.Eminus;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import org.jspecify.annotations.Nullable;

public final class TintSweep {
    private TintSweep() {
    }

    public static List<Block> sweep(Iterable<BlockState> states, BlockColors blockColors,
            Function<FluidState, @Nullable BlockTintSource> fluidTints, UnaryOperator<BlockState> shapes,
            BiomeColours colours) {
        Map<BiomeColours.Colours, Set<Block>> users = new HashMap<>();

        for (BlockState state : states) {
            BlockState shape = shapes.apply(state);
            for (BlockTintSource tint : blockColors.getTintSources(shape)) {
                count(users, colours, tint, shape, state.getBlock());
            }

            FluidState fluid = state.getFluidState();
            BlockTintSource fluidTint = fluid.isEmpty() ? null : fluidTints.apply(fluid);
            if (fluidTint != null) {
                count(users, colours, fluidTint, state, state.getBlock());
            }
        }

        List<BiomeColours.Colours> ranked = users.keySet().stream()
                .sorted(Comparator.comparingInt((BiomeColours.Colours set) -> users.get(set).size()).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        int kept = Math.min(ranked.size(), BiomeColours.MAX_ROWS);
        colours.assign(ranked.subList(0, kept));

        List<Block> dropped = ranked.subList(kept, ranked.size()).stream()
                .flatMap(set -> users.get(set).stream())
                .distinct()
                .sorted(Comparator.comparing(BuiltInRegistries.BLOCK::getKey))
                .toList();
        if (!dropped.isEmpty()) {
            Eminus.LOGGER.warn("More than {} biome colour sets are in use; these blocks draw untinted: {}",
                    BiomeColours.MAX_ROWS,
                    dropped.stream().map(block -> BuiltInRegistries.BLOCK.getKey(block).toString())
                            .collect(Collectors.joining(", ")));
        }

        return dropped;
    }

    private static void count(Map<BiomeColours.Colours, Set<Block>> users, BiomeColours colours,
            BlockTintSource tint, BlockState state, Block block) {
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
