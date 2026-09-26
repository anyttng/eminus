package com.eminus.client.model.game;

import com.eminus.model.port.TintBiome;
import com.eminus.model.port.TintSource;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

record GameFluidTint(LoaderModels loader, FluidState fluid) implements TintSource {
    @Override
    public int colour(BlockState state, TintBiome biome, int blockX, int blockZ) {
        return loader.fluidTint(fluid, (BlockAndTintGetter) biome,
                new BlockPos(blockX, GameTintSource.SAMPLE_Y, blockZ));
    }
}
