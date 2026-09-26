package com.eminus.client.model.game;

import java.util.List;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public final class FabricModels implements LoaderModels {
    private static final int STILL_SPRITE = 0;

    @Override
    public void quads(BlockState state, BakedModel model, RandomSource random, List<? super LayeredQuad> into) {
        GameQuads.gatherByBlockLayer(state, model, random, into);
    }

    @Override
    public TextureAtlasSprite stillSprite(FluidState fluid) {
        return handler(fluid).getFluidSprites(null, null, fluid)[STILL_SPRITE];
    }

    @Override
    public int fluidTint(FluidState fluid, BlockAndTintGetter level, BlockPos pos) {
        return handler(fluid).getFluidColor(level, pos, fluid);
    }

    private static FluidRenderHandler handler(FluidState fluid) {
        FluidRenderHandler own = FluidRenderHandlerRegistry.INSTANCE.get(fluid.getType());
        if (own != null) {
            return own;
        }

        return FluidRenderHandlerRegistry.INSTANCE.get(fluid.is(FluidTags.LAVA) ? Fluids.LAVA : Fluids.WATER);
    }
}
