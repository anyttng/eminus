package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.frame.GameFrames;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("HEAD"))
    private static void eminus$clearFogBranch(Camera camera, FogRenderer.FogMode mode, float renderDistance,
            boolean foggy, float partialTick, CallbackInfo callback) {
        GameFrames.markRenderDistanceFog(false);
    }

    @Inject(method = "setupFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"))
    private static void eminus$markRenderDistanceFog(Camera camera, FogRenderer.FogMode mode, float renderDistance,
            boolean foggy, float partialTick, CallbackInfo callback) {
        GameFrames.markRenderDistanceFog(true);
    }
}
