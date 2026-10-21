package com.eminus.client.gpu.opengl;

import com.eminus.gpu.pipeline.PipelineSpec;

public final class TextureUnitBudget {
    public static void require(PipelineSpec spec, int firstUnit) {
        OpenGlPipeline.requireTextureUnits(spec, firstUnit);
    }

    private TextureUnitBudget() {
    }
}
