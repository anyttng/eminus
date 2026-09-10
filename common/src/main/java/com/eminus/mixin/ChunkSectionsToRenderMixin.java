package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.eminus.session.client.ClientSession;

import com.mojang.blaze3d.textures.GpuSampler;

import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;

@Mixin(ChunkSectionsToRender.class)
public class ChunkSectionsToRenderMixin {
    @Inject(method = "renderGroup", at = @At("RETURN"))
    private void eminus$drawFarLayer(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo callback) {
        if (group == ChunkSectionLayerGroup.OPAQUE) {
            ClientSession.drawFarLayer();
        }
    }
}
