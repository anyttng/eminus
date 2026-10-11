package com.eminus.client.model.game;

import com.eminus.mixin.SpriteContentsAccessor;
import com.eminus.model.port.MipStrategy;
import com.eminus.model.port.Sprite;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

record GameSprite(TextureAtlasSprite sprite) implements Sprite {
    private static final int BASE_MIP_LEVEL = 0;

    @Override
    public int width() {
        return sprite.contents().width();
    }

    @Override
    public int height() {
        return sprite.contents().height();
    }

    @Override
    public float u0() {
        return sprite.getU0();
    }

    @Override
    public float u1() {
        return sprite.getU1();
    }

    @Override
    public float v0() {
        return sprite.getV0();
    }

    @Override
    public float v1() {
        return sprite.getV1();
    }

    @Override
    public int[] argb() {
        SpriteContents contents = sprite.contents();
        NativeImage image = ((SpriteContentsAccessor) contents).eminus$byMipLevel()[BASE_MIP_LEVEL];
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

    @Override
    public MipStrategy mipStrategy() {
        SpriteContents contents = sprite.contents();
        return switch (((SpriteContentsAccessor) contents).eminus$mipmapStrategy()) {
            case AUTO -> contents.transparency().hasTransparent() ? MipStrategy.CUTOUT : MipStrategy.MEAN;
            case MEAN -> MipStrategy.MEAN;
            case CUTOUT -> MipStrategy.CUTOUT;
            case STRICT_CUTOUT -> MipStrategy.STRICT_CUTOUT;
            case DARK_CUTOUT -> MipStrategy.DARK_CUTOUT;
        };
    }

    @Override
    public float alphaCutoffBias() {
        return ((SpriteContentsAccessor) sprite.contents()).eminus$alphaCutoffBias();
    }
}
