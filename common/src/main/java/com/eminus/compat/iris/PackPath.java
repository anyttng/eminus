package com.eminus.compat.iris;

import java.util.List;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

import com.eminus.client.render.far.FarDraw;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;

import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.ints.IntSet;

import org.jspecify.annotations.Nullable;

public interface PackPath {
    String file();

    boolean distantHorizons();

    PackSlot translucentSlot();

    @Nullable ToIntFunction<BlockState> blockIds(IrisRenderingPipeline pipeline,
            @Nullable ToIntFunction<BlockState> rendered);

    IntSet movedIds();

    @Nullable Source source(PackProgram.Kind kind);

    static String header(String ours) {
        return PackSources.header(JcppProcessor.glslPreprocessSource(ours, List.of()));
    }

    record Source(String programName, String file, UnaryOperator<String> fragment,
            @Nullable UnaryOperator<String> vertex, FarDraw.PackVertex vertexStage) {
    }
}
