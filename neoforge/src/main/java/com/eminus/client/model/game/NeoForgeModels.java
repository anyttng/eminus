package com.eminus.client.model.game;

import java.util.List;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;

public final class NeoForgeModels implements LoaderModels {
    @Override
    public void quads(BlockState state, BakedModel model, RandomSource random, List<? super LayeredQuad> into) {
        ReplayRandom replay = new ReplayRandom(random);
        for (RenderType type : model.getRenderTypes(state, replay, ModelData.EMPTY)) {
            GameQuads.gather(replay, type == RenderType.translucent(),
                    (side, sideRandom) -> model.getQuads(state, side, sideRandom, ModelData.EMPTY, type), into);
        }
    }

    @Override
    public TextureAtlasSprite stillSprite(FluidState fluid) {
        return FluidSpriteCache.getSprite(IClientFluidTypeExtensions.of(fluid).getStillTexture());
    }

    @Override
    public int fluidTint(FluidState fluid, BlockAndTintGetter level, BlockPos pos) {
        return IClientFluidTypeExtensions.of(fluid).getTintColor(fluid, level, pos);
    }
}
