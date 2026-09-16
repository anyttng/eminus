package com.eminus.model;

import com.eminus.mixin.SpriteContentsAccessor;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class SpriteSampler {
    private static final int BASE_MIP_LEVEL = 0;

    public static int[] texels(TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        NativeImage image = image(contents);
        int width = contents.width();
        int height = contents.height();
        int[] texels = new int[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                texels[y * width + x] = image.getPixel(x, y);
            }
        }

        return texels;
    }

    public static int index(TextureAtlasSprite sprite, float u, float v) {
        SpriteContents contents = sprite.contents();
        int x = texel(u, sprite.getU0(), sprite.getU1(), contents.width());
        int y = texel(v, sprite.getV0(), sprite.getV1(), contents.height());
        return y * contents.width() + x;
    }

    private static NativeImage image(SpriteContents contents) {
        return ((SpriteContentsAccessor) contents).eminus$byMipLevel()[BASE_MIP_LEVEL];
    }

    private static int texel(float coordinate, float start, float end, int size) {
        float span = end - start;
        int index = span == 0.0F ? 0 : (int) ((coordinate - start) / span * size);
        return Math.clamp(index, 0, size - 1);
    }

    private SpriteSampler() {
    }
}
