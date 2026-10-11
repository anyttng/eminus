package com.eminus.client.model.game;

import com.eminus.mixin.MipmapGeneratorAccessor;
import com.eminus.mixin.SpriteContentsAccessor;
import com.eminus.model.port.Sprite;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.util.FastColor;

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
                    levels[level][y * width + x] = FastColor.ABGR32.fromArgb32(image.getPixelRGBA(x, y));
                }
            }
        }

        return levels;
    }

    // Sodium and Iris overwrite this method, so the far atlas calls it rather than copying vanilla's blend.
    public static int blend(int first, int second, int third, int fourth, boolean cutout) {
        return FastColor.ABGR32.fromArgb32(MipmapGeneratorAccessor.eminus$alphaBlend(
                FastColor.ABGR32.fromArgb32(first), FastColor.ABGR32.fromArgb32(second),
                FastColor.ABGR32.fromArgb32(third), FastColor.ABGR32.fromArgb32(fourth), cutout));
    }

    private SpriteMips() {
    }
}
