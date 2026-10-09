package com.eminus.compat.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.eminus.compat.iris.ViewPosition;

import net.irisshaders.iris.pipeline.transform.TransformPatcher;

@Mixin(TransformPatcher.class)
public class TransformPatcherMixin {
    private static final String TRANSFORM = "transform(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
            + "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
            + "Lnet/irisshaders/iris/pipeline/transform/parameter/Parameters;)Ljava/util/Map;";
    private static final String TRANSFORM_COMPUTE = "transformCompute(Ljava/lang/String;Ljava/lang/String;"
            + "Lnet/irisshaders/iris/pipeline/transform/parameter/Parameters;)Ljava/util/Map;";
    private static final String PUT_STAGE = "Ljava/util/EnumMap;put(Ljava/lang/Enum;Ljava/lang/Object;)"
            + "Ljava/lang/Object;";
    private static final int STAGE_SOURCE = 1;

    @ModifyArg(method = { TRANSFORM, TRANSFORM_COMPUTE }, at = @At(value = "INVOKE", target = PUT_STAGE),
            index = STAGE_SOURCE)
    private static Object eminus$spliceViewPosition(Object source) {
        return source instanceof String stage ? ViewPosition.splice(stage) : source;
    }
}
