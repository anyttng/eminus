package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.session.client.ClientSession;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import org.joml.Matrix4f;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    private static final String LEVEL_RENDER = "Lnet/minecraft/client/renderer/LevelRenderer;render("
            + "Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;Z"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;"
            + "Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V";

    @Inject(method = "extract", at = @At("RETURN"))
    private void eminus$overrideNearField(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo callback) {
        ClientSession.overrideNearField();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = LEVEL_RENDER))
    private void eminus$captureLevelProjection(DeltaTracker deltaTracker, CallbackInfo callback,
            @Local Matrix4f levelProjection, @Local CameraRenderState cameraState) {
        ClientSession.captureLevelProjection(levelProjection, cameraState.projectionMatrix);
    }
}
