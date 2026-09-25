package com.eminus.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;

@Mixin(RenderSystem.class)
public interface RenderSystemAccessor {
    @Accessor("currentPipelineCache")
    static @Nullable PipelineCache eminus$currentPipelineCache() {
        throw new AssertionError();
    }

    @Accessor("fallbackPipelineCache")
    static @Nullable PipelineCache eminus$fallbackPipelineCache() {
        throw new AssertionError();
    }
}
