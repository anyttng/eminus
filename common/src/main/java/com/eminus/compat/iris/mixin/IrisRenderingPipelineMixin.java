package com.eminus.compat.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.session.ClientSession;
import com.eminus.compat.iris.IrisShaderPack;

@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline")
public class IrisRenderingPipelineMixin {
    private static final String FINALIZE = "finalizeLevelRendering";

    @Inject(method = "beginTranslucents", at = @At("HEAD"))
    private void eminus$drawOpaqueFarLayerInPack(CallbackInfo callback) {
        ClientSession.drawFarLayerInShaderPack(this, false);
    }

    @Inject(method = FINALIZE, at = @At("HEAD"))
    private void eminus$drawTranslucentFarLayerInPack(CallbackInfo callback) {
        ClientSession.drawFarLayerInShaderPack(this, true);
    }

    @Inject(method = FINALIZE, at = @At("RETURN"))
    private void eminus$drawFarLayerOverPackFrame(CallbackInfo callback) {
        ClientSession.drawFarLayerOverShaderPack();
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void eminus$forgetPackLayer(CallbackInfo callback) {
        IrisShaderPack.pipelineDestroyed(this);
    }
}
