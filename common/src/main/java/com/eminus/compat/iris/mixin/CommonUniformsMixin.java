package com.eminus.compat.iris.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.compat.iris.IrisFarState;
import com.eminus.compat.iris.PackContract;

import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;

@Mixin(CommonUniforms.class)
public class CommonUniformsMixin {
    @Inject(method = "generalCommonUniforms", at = @At("RETURN"))
    private static void eminus$addFarUniforms(UniformHolder uniforms, FrameUpdateNotifier updateNotifier,
            PackDirectives directives, CallbackInfo callback) {
        uniforms.uniformMatrix(UniformUpdateFrequency.PER_FRAME, PackContract.PROJECTION, IrisFarState::projection)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, PackContract.PROJECTION_INVERSE,
                        IrisFarState::projectionInverse)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, PackContract.PREVIOUS_PROJECTION,
                        IrisFarState::previousProjection)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, PackContract.RENDER_DISTANCE,
                        IrisFarState::renderDistance)
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, PackContract.VIEW_NEAR_PROJECTION_INVERSE,
                        () -> CapturedRenderingState.INSTANCE.getGbufferProjection().invert(new Matrix4f()))
                .uniformMatrix(UniformUpdateFrequency.PER_FRAME, PackContract.VIEW_FAR_PROJECTION_INVERSE,
                        IrisFarState::projectionInverse)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, PackContract.VIEW_FAR_DISTANCE,
                        IrisFarState::renderDistance);
    }
}
