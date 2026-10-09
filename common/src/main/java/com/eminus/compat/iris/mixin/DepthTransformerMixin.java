package com.eminus.compat.iris.mixin;

import java.util.HashSet;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.eminus.compat.iris.PackContract;

import net.irisshaders.iris.pipeline.transform.transformer.DepthTransformer;

@Mixin(DepthTransformer.class)
public class DepthTransformerMixin {
    private static final String TRANSFORM = "transform(Lio/github/douira/glsl_transformer/ast/transform/ASTParser;"
            + "Lio/github/douira/glsl_transformer/ast/node/TranslationUnit;"
            + "Lio/github/douira/glsl_transformer/ast/query/Root;"
            + "Lnet/irisshaders/iris/pipeline/transform/PatchShaderType;ZZLjava/util/Set;)V";
    private static final String INVERT_DEPTH_READS = "Lnet/irisshaders/iris/pipeline/transform/transformer/"
            + "DepthTransformer;invertDepthReads(Lio/github/douira/glsl_transformer/ast/transform/ASTParser;"
            + "Lio/github/douira/glsl_transformer/ast/node/TranslationUnit;"
            + "Lio/github/douira/glsl_transformer/ast/query/Root;Ljava/util/Set;)V";
    private static final int DEPTH_SAMPLERS = 3;

    @ModifyArg(method = TRANSFORM, at = @At(value = "INVOKE", target = INVERT_DEPTH_READS), index = DEPTH_SAMPLERS)
    private static Set<String> eminus$invertFarDepthReads(Set<String> depthSamplers) {
        Set<String> inverted = new HashSet<>(depthSamplers);
        inverted.addAll(PackContract.SAMPLERS);
        return inverted;
    }
}
