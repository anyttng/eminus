package com.eminus.client.model.game;

import com.eminus.model.port.TintBiome;
import com.eminus.model.port.TintSource;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

record GameTintSource(BlockColors colors, int layer) implements TintSource {
    static final int SAMPLE_Y = 0;

    @Override
    public int color(BlockState state, TintBiome biome, int blockX, int blockZ) {
        return colors.getColor(state, (BlockAndTintGetter) biome, new BlockPos(blockX, SAMPLE_Y, blockZ), layer);
    }
}
