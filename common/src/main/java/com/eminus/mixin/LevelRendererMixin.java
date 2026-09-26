package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.eminus.client.session.ClientSession;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;

import org.joml.Matrix4f;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    private static final String RENDER_SECTION_LAYER = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer("
            + "Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V";
    private static final String SETUP_FOG = "Lnet/minecraft/client/renderer/FogRenderer;setupFog("
            + "Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V";

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = RENDER_SECTION_LAYER))
    private void eminus$drawFarLayer(LevelRenderer renderer, RenderType layer, double cameraX, double cameraY,
            double cameraZ, Matrix4f viewRotation, Matrix4f projection, Operation<Void> original) {
        original.call(renderer, layer, cameraX, cameraY, cameraZ, viewRotation, projection);
        if (layer == RenderType.cutout()) {
            ClientSession.drawFarLayer();
        }
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = SETUP_FOG))
    private void eminus$overrideNearField(Camera camera, FogRenderer.FogMode mode, float renderDistance,
            boolean foggy, float partialTick, Operation<Void> original) {
        original.call(camera, mode, renderDistance, foggy, partialTick);
        if (mode == FogRenderer.FogMode.FOG_TERRAIN) {
            ClientSession.overrideNearField();
        }
    }
}
