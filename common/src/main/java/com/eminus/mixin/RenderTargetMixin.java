package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.gpu.opengl.RenderTargetGeneration;

import com.mojang.blaze3d.pipeline.RenderTarget;

@Mixin(RenderTarget.class)
public class RenderTargetMixin implements RenderTargetGeneration {
    @Unique
    private int eminus$generation;

    @Inject(method = "createBuffers", at = @At("RETURN"))
    private void eminus$countAllocation(int width, int height, boolean clearError, CallbackInfo callback) {
        eminus$generation++;
    }

    @Unique
    @Override
    public int eminus$generation() {
        return eminus$generation;
    }
}
