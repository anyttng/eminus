package com.eminus.model;

import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface StateBaker {
    BakedModel bake(BlockState state);
}
