package com.eminus.compat.iris.mixin;

import java.util.HashSet;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.eminus.compat.iris.PackContract;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.irisshaders.iris.pipeline.transform.transformer.DepthTransformer;

import org.jspecify.annotations.Nullable;

@Mixin(DepthTransformer.class)
public class DepthTransformerMixin {
    private static final String DEPTH_SAMPLERS =
            "Lnet/irisshaders/iris/pipeline/transform/transformer/DepthTransformer;DEPTH_SAMPLERS:Ljava/util/Set;";

    @Unique
    private static @Nullable Set<String> eminus$inverted;

    // Iris 1.11.4 reads its depth sampler names from this constant alone; 1.11.7 passes them in as a set.
    @ModifyExpressionValue(method = "isDepthSampler", at = @At(value = "FIELD", target = DEPTH_SAMPLERS))
    private static Set<String> eminus$invertFarDepthReads(Set<String> depthSamplers) {
        Set<String> inverted = eminus$inverted;
        if (inverted == null) {
            Set<String> union = new HashSet<>(depthSamplers);
            union.addAll(PackContract.SAMPLERS);
            inverted = Set.copyOf(union);
            eminus$inverted = inverted;
        }
        return inverted;
    }
}
