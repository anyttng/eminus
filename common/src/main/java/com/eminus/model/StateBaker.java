package com.eminus.model;

import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface StateBaker {
    BakedState bake(BlockState state);

    default void pick(BlockState state, RandomSource random, List<Object> parts) {
        throw new UnsupportedOperationException("This baker classifies no state as positional.");
    }

    default BakedModel bakeParts(BlockState state, List<Object> parts) {
        throw new UnsupportedOperationException("This baker classifies no state as positional.");
    }
}
