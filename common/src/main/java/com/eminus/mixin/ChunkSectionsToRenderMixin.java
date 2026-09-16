package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.eminus.client.session.ClientSession;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.textures.GpuSampler;

import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;

@Mixin(ChunkSectionsToRender.class)
public class ChunkSectionsToRenderMixin {
    // A wrap and not a RETURN inject: Sodium cancels this method at its head, which skips every RETURN.
    @WrapMethod(method = "renderGroup")
    private void eminus$drawFarLayer(ChunkSectionLayerGroup group, GpuSampler sampler, Operation<Void> original) {
        original.call(group, sampler);
        if (group == ChunkSectionLayerGroup.OPAQUE) {
            ClientSession.drawFarLayer();
        }
    }
}
