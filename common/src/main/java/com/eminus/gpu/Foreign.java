package com.eminus.gpu;

import java.util.function.UnaryOperator;

import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Texture;

public interface Foreign {
    Pipeline pipeline(PipelineSpec spec, int firstTextureUnit, UnaryOperator<String> fragment);

    int program(Pipeline pipeline);

    int textureUnits(Pipeline pipeline);

    int texture(Texture texture);

    Pass pass(int framebuffer, int width, int height, int colourTargets);

    void copyDepth(Texture colour, Texture from, Texture to);
}
