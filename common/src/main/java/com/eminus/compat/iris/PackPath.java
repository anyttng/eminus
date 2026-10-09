package com.eminus.compat.iris;

import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;

import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

interface PackPath {
    String file();

    @Nullable ToIntFunction<BlockState> blockIds(IrisRenderingPipeline pipeline,
            @Nullable ToIntFunction<BlockState> rendered);

    @Nullable Source source(PackProgram.Kind kind);

    record Source(String programName, String file, UnaryOperator<String> fragment,
            @Nullable UnaryOperator<String> vertex) {
    }
}
