package com.eminus.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public interface GlDeviceAccessor {
    @Accessor("pipelineCache")
    Map<RenderPipeline, GlRenderPipeline> eminus$pipelineCache();
}
