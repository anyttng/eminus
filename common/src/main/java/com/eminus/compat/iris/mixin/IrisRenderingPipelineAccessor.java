package com.eminus.compat.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;

@Mixin(IrisRenderingPipeline.class)
public interface IrisRenderingPipelineAccessor {
    @Accessor("initializedBlockIds")
    boolean eminus$initializedBlockIds();
}
