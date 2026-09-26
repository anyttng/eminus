package com.eminus.model.port;

import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface TintSource {
    int colour(BlockState state, TintBiome biome, int blockX, int blockZ);
}
