package com.eminus.client.model.game;

import java.util.List;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public interface LoaderModels {
    void quads(BlockState state, BakedModel model, RandomSource random, List<? super LayeredQuad> into);

    TextureAtlasSprite stillSprite(FluidState fluid);

    int fluidTint(FluidState fluid, BlockAndTintGetter level, BlockPos pos);
}
