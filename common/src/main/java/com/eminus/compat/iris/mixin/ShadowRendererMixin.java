package com.eminus.compat.iris.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.session.ClientSession;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.shadows.ShadowRenderer;

@Mixin(ShadowRenderer.class)
public class ShadowRendererMixin {
    private static final String TERRAIN_DRAWN = "Lnet/irisshaders/iris/uniforms/CapturedRenderingState;getTickDelta()F";

    @Shadow
    @Final
    private IrisRenderingPipeline pipeline;

    @Inject(method = "renderShadows", at = @At(value = "INVOKE", target = TERRAIN_DRAWN, ordinal = 0))
    private void eminus$drawFarLayerInShadowPass(CallbackInfo callback) {
        pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
        ClientSession.drawFarLayerInShadowPass(ShadowRenderer.MODELVIEW, ShadowRenderer.PROJECTION);
        pipeline.setPhase(WorldRenderingPhase.NONE);
    }
}
