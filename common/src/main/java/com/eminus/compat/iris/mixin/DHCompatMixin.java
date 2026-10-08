package com.eminus.compat.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.eminus.compat.iris.IrisShaderPack;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(targets = "net.irisshaders.iris.compat.dh.DHCompat")
public class DHCompatMixin {
    @ModifyReturnValue(method = "getRenderDistance()I", at = @At("RETURN"))
    private static int eminus$reachFarLayerShadows(int chunks) {
        return IrisShaderPack.shadowReach(chunks);
    }
}
