package com.eminus.model.port;

import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface TintSource {
    int color(BlockState state, TintBiome biome, int blockX, int blockZ);
}
