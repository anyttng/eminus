package com.eminus.model;

import java.lang.reflect.Method;

import com.eminus.model.port.MipBlend;

import net.minecraft.client.renderer.texture.MipmapGenerator;

final class VanillaMipBlend {
    private static final String GAME_BLEND = "alphaBlend";

    static final MipBlend BLEND = vanilla();

    private static MipBlend vanilla() {
        try {
            Method blend = MipmapGenerator.class.getDeclaredMethod(GAME_BLEND,
                    int.class, int.class, int.class, int.class, boolean.class);
            blend.setAccessible(true);
            return (first, second, third, fourth, cutout) -> {
                try {
                    return (int) blend.invoke(null, first, second, third, fourth, cutout);
                } catch (ReflectiveOperationException unreachable) {
                    throw new IllegalStateException(unreachable);
                }
            };
        } catch (NoSuchMethodException absent) {
            throw new IllegalStateException(absent);
        }
    }

    private VanillaMipBlend() {
    }
}
