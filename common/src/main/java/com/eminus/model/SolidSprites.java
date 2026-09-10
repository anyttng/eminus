package com.eminus.model;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class SolidSprites {
    private final Map<TextureAtlasSprite, int[]> texels = new IdentityHashMap<>();

    public int argb(TextureAtlasSprite sprite, float u, float v) {
        int[] solid = texels.computeIfAbsent(sprite, SolidSprites::solidified);
        return solid[SpriteSampler.index(sprite, u, v)];
    }

    private static int[] solidified(TextureAtlasSprite sprite) {
        int[] copy = SpriteSampler.texels(sprite);
        Solidify.apply(copy, sprite.contents().width(), sprite.contents().height());
        return copy;
    }
}
