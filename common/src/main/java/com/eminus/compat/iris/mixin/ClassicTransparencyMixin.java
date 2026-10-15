package com.eminus.compat.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.client.session.ClientSession;
import com.eminus.compat.iris.PackSlot;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;

import net.minecraft.client.renderer.LevelRenderer;

@Mixin(LevelRenderer.class)
public class ClassicTransparencyMixin {
    private static final String CLASSIC_TRANSPARENCY = "executeClassicTransparency";
    private static final String TRANSLUCENT_TERRAIN =
            "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup("
                    + "Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;"
                    + "Lcom/mojang/renderpearl/api/commands/RenderPass;"
                    + "Lcom/mojang/renderpearl/api/textures/GpuSampler;"
                    + "Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V";

    @Inject(method = CLASSIC_TRANSPARENCY, at = @At(value = "INVOKE", target = TRANSLUCENT_TERRAIN))
    private void eminus$drawTranslucentFarLayerBeforeNearTerrain(CallbackInfo callback) {
        if (Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline) {
            ClientSession.drawFarLayerInShaderPack(pipeline, PackSlot.BEFORE_NEAR_TRANSLUCENT);
        }
    }
}
