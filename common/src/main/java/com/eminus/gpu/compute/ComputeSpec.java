package com.eminus.gpu.compute;

import java.util.List;

import com.eminus.gpu.pipeline.Binding;
import com.eminus.gpu.pipeline.PipelineSpec;

import net.minecraft.resources.ResourceLocation;

public record ComputeSpec(ResourceLocation location, ResourceLocation shader, List<Binding> bindings,
        List<PipelineSpec.Define> defines) {
}
