package com.eminus.client.model.game;

import com.eminus.model.port.FluidModel;
import com.eminus.model.port.FluidModels;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.world.level.material.FluidState;

public record GameFluids(FluidStateModelSet models) implements FluidModels {
    @Override
    public FluidModel model(FluidState fluid) {
        net.minecraft.client.renderer.block.FluidModel model = models.get(fluid);
        BlockTintSource tint = model.tintSource();
        return new FluidModel(new GameSprite(model.stillMaterial().sprite()), model.layer().translucent(),
                tint == null ? null : new GameTintSource(tint));
    }
}
