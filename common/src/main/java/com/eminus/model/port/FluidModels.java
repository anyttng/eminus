package com.eminus.model.port;

import net.minecraft.world.level.material.FluidState;

@FunctionalInterface
public interface FluidModels {
    FluidModel model(FluidState fluid);
}
