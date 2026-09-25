package com.eminus.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

@Mixin(PipelineCache.class)
public interface PipelineCacheAccessor {
    @Accessor("cache")
    Map<RenderPipeline, CompiledRenderPipeline> eminus$cache();
}
