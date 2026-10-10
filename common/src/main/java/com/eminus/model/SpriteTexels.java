package com.eminus.model;

import java.util.HashMap;
import java.util.Map;

import com.eminus.model.port.Sprite;

public final class SpriteTexels {
    private final Map<Sprite, int[]> texels = new HashMap<>();

    public int argb(Sprite sprite, float u, float v) {
        return texels.computeIfAbsent(sprite, Sprite::argb)[sprite.index(u, v)];
    }
}
