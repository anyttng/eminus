package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.texture.MipmapGenerator;

@Mixin(MipmapGenerator.class)
public interface MipmapGeneratorAccessor {
    @Invoker("alphaBlend")
    static int eminus$alphaBlend(int first, int second, int third, int fourth, boolean transparent) {
        throw new AssertionError();
    }
}
