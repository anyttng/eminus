package com.eminus.client.model.game;

import com.eminus.mixin.SpriteContentsAccessor;
import com.eminus.model.port.Sprite;

import com.mojang.blaze3d.platform.NativeImage;

public final class SpriteMips {
    public static int[][] levels(Sprite sprite) {
        NativeImage[] images = ((SpriteContentsAccessor) ((GameSprite) sprite).sprite().contents())
                .eminus$byMipLevel();
        int[][] levels = new int[images.length][];

        for (int level = 0; level < images.length; level++) {
            NativeImage image = images[level];
            int width = image.getWidth();
            levels[level] = new int[width * image.getHeight()];
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < width; x++) {
                    levels[level][y * width + x] = image.getPixel(x, y);
                }
            }
        }

        return levels;
    }

    private SpriteMips() {
    }
}
