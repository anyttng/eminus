package com.eminus.model;

import com.eminus.model.port.MipBlend;
import com.eminus.model.port.MipStrategy;

public final class FaceMips {
    public static int[][] colorLevels(int[] face, int side, FaceMip mip, boolean opaque, MipBlend blend) {
        // A face is stored bottom-up; the game's float sums run over the sprite's rows top-down.
        flipRows(face, side);
        int[][] levels = chain(face, side, mip, blend);
        finish(levels, side, opaque);
        return levels;
    }

    private static int[][] chain(int[] face, int side, FaceMip mip, MipBlend blend) {
        boolean cutout = mip.strategy() == MipStrategy.CUTOUT;
        Mips.Mean mean = (first, second, third, fourth) -> blend.blend(first, second, third, fourth, cutout);

        int[][] levels = new int[Mips.levelCount(side)][];
        levels[0] = face;
        for (int level = 1; level < levels.length; level++) {
            levels[level] = Mips.halve(levels[level - 1], side >> (level - 1), mean);
        }

        return levels;
    }

    private static void finish(int[][] levels, int side, boolean opaque) {
        for (int level = 0; level < levels.length; level++) {
            flipRows(levels[level], side >> level);
            if (opaque) {
                for (int texel = 0; texel < levels[level].length; texel++) {
                    levels[level][texel] = Argb.opaque(levels[level][texel]);
                }
            }
        }
    }

    private static void flipRows(int[] texels, int side) {
        for (int top = 0, bottom = side - 1; top < bottom; top++, bottom--) {
            for (int x = 0; x < side; x++) {
                int swapped = texels[top * side + x];
                texels[top * side + x] = texels[bottom * side + x];
                texels[bottom * side + x] = swapped;
            }
        }
    }

    private FaceMips() {
    }
}
