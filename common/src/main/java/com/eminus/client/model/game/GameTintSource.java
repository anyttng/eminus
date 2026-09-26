package com.eminus.client.model.game;

import com.eminus.model.port.TintBiome;
import com.eminus.model.port.TintSource;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

record GameTintSource(BlockTintSource source) implements TintSource {
    private static final int SAMPLE_Y = 0;

    @Override
    public int colour(BlockState state, TintBiome biome, int blockX, int blockZ) {
        return source.colorInWorld(state, (BlockAndTintGetter) biome, new BlockPos(blockX, SAMPLE_Y, blockZ));
    }
}
