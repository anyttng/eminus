package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.eminus.client.frame.GameFrames;
import com.eminus.client.session.ClientSession;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;

import org.joml.Matrix4f;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    private static final String LEVEL_RENDER = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel("
            + "Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;"
            + "Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;"
            + "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V";

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = LEVEL_RENDER))
    private void eminus$captureLevelProjection(LevelRenderer renderer, DeltaTracker deltaTracker,
            boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
            Matrix4f viewRotation, Matrix4f levelProjection, Operation<Void> original, @Local double fov) {
        GameFrames.captureFov(fov);
        ClientSession.captureLevelProjection(levelProjection, gameRenderer.getProjectionMatrix(fov));
        original.call(renderer, deltaTracker, renderBlockOutline, camera, gameRenderer, lightTexture, viewRotation,
                levelProjection);
    }
}
