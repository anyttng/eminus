package com.eminus.compat.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.session.ClientSession;

@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline")
public class IrisRenderingPipelineMixin {
    @Inject(method = "finalizeLevelRendering", at = @At("RETURN"))
    private void eminus$drawFarLayerOverPackFrame(CallbackInfo callback) {
        ClientSession.drawFarLayerOverShaderPack();
    }
}
