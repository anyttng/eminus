package com.eminus.gpu.pipeline;

import net.minecraft.resources.ResourceLocation;

public interface Pipeline {
    ResourceLocation location();

    boolean compiles();
}
