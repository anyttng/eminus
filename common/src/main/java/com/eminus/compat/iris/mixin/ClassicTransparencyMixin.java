package com.eminus.compat.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.eminus.InjectionTargets;
import com.eminus.client.session.ClientSession;
import com.eminus.compat.iris.PackSlot;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;

import org.joml.Matrix4f;

@Mixin(LevelRenderer.class)
public class ClassicTransparencyMixin {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = InjectionTargets.RENDER_SECTION_LAYER))
    private void eminus$drawTranslucentFarLayerBeforeNearTerrain(LevelRenderer renderer, RenderType layer,
            double cameraX, double cameraY, double cameraZ, Matrix4f viewRotation, Matrix4f projection,
            Operation<Void> original) {
        if (layer == RenderType.translucent()
                && Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline) {
            ClientSession.drawFarLayerInShaderPack(pipeline, PackSlot.BEFORE_NEAR_TRANSLUCENT);
        }
        original.call(renderer, layer, cameraX, cameraY, cameraZ, viewRotation, projection);
    }
}
