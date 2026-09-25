package com.eminus.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;

@Mixin(VulkanDevice.class)
public interface VulkanDeviceAccessor {
    @Accessor("pipelineCache")
    Map<RenderPipeline, VulkanRenderPipeline> eminus$pipelineCache();
}
