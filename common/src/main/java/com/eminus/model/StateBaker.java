package com.eminus.model;

import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface StateBaker {
    BakedState bake(BlockState state);
}
