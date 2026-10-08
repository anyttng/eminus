package com.eminus.gpu.compute;

import java.util.List;

import com.eminus.gpu.Location;
import com.eminus.gpu.pipeline.Binding;
import com.eminus.gpu.pipeline.PipelineSpec;

public record ComputeSpec(Location location, Location shader, List<Binding> bindings,
        List<PipelineSpec.Define> defines) {
}
