package com.eminus.client.model.game;

import com.eminus.model.port.FluidModel;
import com.eminus.model.port.FluidModels;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.material.FluidState;

public record GameFluids(LoaderModels loader) implements FluidModels {
    @Override
    public FluidModel model(FluidState fluid) {
        return new FluidModel(new GameSprite(loader.stillSprite(fluid)),
                ItemBlockRenderTypes.getRenderLayer(fluid) == RenderType.translucent(),
                new GameFluidTint(loader, fluid));
    }
}
