package com.eminus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.opengl.GlBuffer;

@Mixin(GlBuffer.class)
public interface GlBufferAccessor {
    @Accessor("handle")
    int eminus$handle();
}
