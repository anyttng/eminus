package com.eminus.compat.vitrail.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.session.ClientSession;

import dev.vitrail.platform.EngineStages;

@Mixin(EngineStages.class)
public class EngineStagesMixin {
    @Inject(method = "afterLevel", at = @At("RETURN"))
    private static void eminus$drawFarLayerOverPackFrame(CallbackInfo callback) {
        ClientSession.drawFarLayerOverShaderPack();
    }
}
