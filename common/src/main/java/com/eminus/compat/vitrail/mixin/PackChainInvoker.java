package com.eminus.compat.vitrail.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import dev.vitrail.render.PackChain;

@Mixin(PackChain.class)
public interface PackChainInvoker {
    @Invoker("drawingPack")
    static boolean eminus$drawingPack() {
        throw new AssertionError();
    }
}
