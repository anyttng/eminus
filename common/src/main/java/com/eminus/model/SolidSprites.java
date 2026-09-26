package com.eminus.model;

import java.util.HashMap;
import java.util.Map;

import com.eminus.model.port.Sprite;

public final class SolidSprites {
    private final Map<Sprite, int[]> texels = new HashMap<>();

    public int argb(Sprite sprite, float u, float v) {
        int[] solid = texels.computeIfAbsent(sprite, SolidSprites::solidified);
        return solid[sprite.index(u, v)];
    }

    private static int[] solidified(Sprite sprite) {
        int[] copy = sprite.argb();
        Solidify.apply(copy, sprite.width(), sprite.height());
        return copy;
    }
}
