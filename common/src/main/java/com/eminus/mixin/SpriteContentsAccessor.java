package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.SpriteContents;

@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor("byMipLevel")
    NativeImage[] eminus$byMipLevel();

    @Accessor("mipmapStrategy")
    MipmapStrategy eminus$mipmapStrategy();

    @Accessor("alphaCutoffBias")
    float eminus$alphaCutoffBias();
}
