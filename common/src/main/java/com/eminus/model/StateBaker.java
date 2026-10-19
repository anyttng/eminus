package com.eminus.model;

import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface StateBaker {
    BakedState bake(BlockState state);

    default @Nullable BakedState bakeOpaqueTwin(BlockState state) {
        return null;
    }

    default void pick(BlockState state, RandomSource random, List<Object> parts) {
        throw new UnsupportedOperationException("This baker classifies no state as positional.");
    }

    default BakedModel bakeParts(BlockState state, List<Object> parts) {
        throw new UnsupportedOperationException("This baker classifies no state as positional.");
    }

    default @Nullable BakedModel bakeOpaqueTwinParts(BlockState state, List<Object> parts) {
        return null;
    }
}
