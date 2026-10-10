package com.eminus.model;

import com.eminus.model.port.MipStrategy;

public final class FaceMips {
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int DARK_NUMERATOR = 3;
    private static final int DARK_DENOMINATOR = 4;
    private static final int UNDRAWN_DARKEST = -1;
    private static final float CUTOUT_REFERENCE = 0.5F;
    private static final float STRICT_CUTOUT_REFERENCE = 0.3F;
    private static final int SUBSAMPLES = 4;
    private static final float SUBSAMPLE_CELLS = SUBSAMPLES * SUBSAMPLES;
    private static final float SUBSAMPLE_CENTRE = 0.5F;
    private static final int SCALE_STEPS = 5;
    private static final float MAX_ALPHA_SCALE = 4.0F;
    private static final float ALPHA_LIFT = 0.025F;

    public static int[][] colourLevels(int[] face, int side, FaceMip mip, boolean opaque) {
        // A face is stored bottom-up; the game's float sums run over the sprite's rows top-down.
        flipRows(face, side);
        MipStrategy strategy = mip.strategy();
        switch (strategy) {
            case CUTOUT, STRICT_CUTOUT -> Solidify.apply(face, side, side);
            case DARK_CUTOUT -> fillEmptyDark(face, side);
            case MEAN -> { }
        }

        boolean cutout = strategy != MipStrategy.MEAN;
        float reference = strategy == MipStrategy.STRICT_CUTOUT ? STRICT_CUTOUT_REFERENCE : CUTOUT_REFERENCE;
        float coverage = cutout ? coverage(face, side, reference, 1.0F) : 0.0F;
        Mips.Mean mean = strategy == MipStrategy.DARK_CUTOUT ? Argb::darkenedAlphaBlend : Argb::meanLinear;

        int[][] levels = new int[Mips.levelCount(side)][];
        levels[0] = face;
        for (int level = 1; level < levels.length; level++) {
            levels[level] = Mips.halve(levels[level - 1], side >> (level - 1), mean);
            if (cutout) {
                scaleAlphaToCoverage(levels[level], side >> level, coverage, reference, mip.alphaCutoffBias());
            }
        }

        for (int level = 0; level < levels.length; level++) {
            flipRows(levels[level], side >> level);
            if (opaque) {
                for (int texel = 0; texel < levels[level].length; texel++) {
                    levels[level][texel] = Argb.opaque(levels[level][texel]);
                }
            }
        }

        return levels;
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

    private static void fillEmptyDark(int[] face, int side) {
        int darkest = UNDRAWN_DARKEST;
        int minBrightness = Integer.MAX_VALUE;

        for (int column = 0; column < side; column++) {
            for (int texel = column; texel < face.length; texel += side) {
                int argb = face[texel];
                if ((argb & ALPHA_MASK) != 0 && brightness(argb) < minBrightness) {
                    minBrightness = brightness(argb);
                    darkest = argb;
                }
            }
        }

        int dark = darkened(darkest, RED_SHIFT) << RED_SHIFT | darkened(darkest, GREEN_SHIFT) << GREEN_SHIFT
                | darkened(darkest, 0);
        for (int texel = 0; texel < face.length; texel++) {
            if ((face[texel] & ALPHA_MASK) == 0) {
                face[texel] = dark;
            }
        }
    }

    private static int brightness(int argb) {
        return (argb >>> RED_SHIFT & CHANNEL_MASK) + (argb >>> GREEN_SHIFT & CHANNEL_MASK) + (argb & CHANNEL_MASK);
    }

    private static int darkened(int argb, int shift) {
        return DARK_NUMERATOR * (argb >>> shift & CHANNEL_MASK) / DARK_DENOMINATOR;
    }

    private static float coverage(int[] level, int side, float reference, float scale) {
        float coverage = 0.0F;

        for (int y = 0; y < side - 1; y++) {
            for (int x = 0; x < side - 1; x++) {
                float alpha00 = scaled(level[y * side + x], scale);
                float alpha10 = scaled(level[y * side + x + 1], scale);
                float alpha01 = scaled(level[(y + 1) * side + x], scale);
                float alpha11 = scaled(level[(y + 1) * side + x + 1], scale);
                float texelCoverage = 0.0F;

                for (int subsampleY = 0; subsampleY < SUBSAMPLES; subsampleY++) {
                    float fy = (subsampleY + SUBSAMPLE_CENTRE) / SUBSAMPLES;
                    for (int subsampleX = 0; subsampleX < SUBSAMPLES; subsampleX++) {
                        float fx = (subsampleX + SUBSAMPLE_CENTRE) / SUBSAMPLES;
                        float alpha = alpha00 * (1.0F - fx) * (1.0F - fy) + alpha10 * fx * (1.0F - fy)
                                + alpha01 * (1.0F - fx) * fy + alpha11 * fx * fy;
                        if (alpha > reference) {
                            texelCoverage++;
                        }
                    }
                }

                coverage += texelCoverage / SUBSAMPLE_CELLS;
            }
        }

        return coverage / ((side - 1) * (side - 1));
    }

    private static float scaled(int argb, float scale) {
        return Math.clamp(Argb.alphaFloat(argb) * scale, 0.0F, 1.0F);
    }

    private static void scaleAlphaToCoverage(int[] level, int side, float desired, float reference, float bias) {
        float minScale = 0.0F;
        float maxScale = MAX_ALPHA_SCALE;
        float scale = 1.0F;
        float bestScale = 1.0F;
        float bestError = Float.MAX_VALUE;

        for (int step = 0; step < SCALE_STEPS; step++) {
            float current = coverage(level, side, reference, scale);
            float error = Math.abs(current - desired);
            if (error < bestError) {
                bestError = error;
                bestScale = scale;
            }

            if (current < desired) {
                minScale = scale;
            } else {
                if (!(current > desired)) {
                    break;
                }

                maxScale = scale;
            }

            scale = (minScale + maxScale) * 0.5F;
        }

        for (int texel = 0; texel < level.length; texel++) {
            float alpha = Argb.alphaFloat(level[texel]) * bestScale + bias + ALPHA_LIFT;
            level[texel] = Argb.withAlpha(Math.clamp(alpha, 0.0F, 1.0F), level[texel]);
        }
    }

    private FaceMips() {
    }
}
