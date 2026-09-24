package com.eminus.gpu.compute;

import java.util.List;

import com.eminus.gpu.pipeline.Binding;
import com.eminus.gpu.pipeline.PipelineSpec;

import net.minecraft.resources.Identifier;

public record ComputeSpec(Identifier location, Identifier shader, List<Binding> bindings,
        List<PipelineSpec.Define> defines) {
}
