package com.eminus.model;

import java.util.Arrays;

import com.eminus.cell.FaceMask;

public record BakedModel(int[] faces, long[] tintMask, float[] insets, float[] bounds, int metadata,
        int tintRow, int[] variants) {
    public static final int FACE_COUNT = 6;
    public static final int FACE_SIDE = 16;
    public static final int FACE_TEXELS = FACE_SIDE * FACE_SIDE;

    public static final int TINT_MASK_WORDS = FACE_COUNT * FACE_TEXELS / Long.SIZE;

    public static final int BOUNDS_LENGTH = 6;
    public static final int MIN_X = 0;
    public static final int MIN_Y = 1;
    public static final int MIN_Z = 2;
    public static final int MAX_X = 3;
    public static final int MAX_Y = 4;
    public static final int MAX_Z = 5;

    public static final float EMPTY_INSET = 1.0F;

    public static final int VARIANT_WORDS = 2;
    public static final int MAX_VARIANT_REJECTIONS = 8;

    private static final int[] NO_VARIANTS = new int[0];

    private static final long ALL_TINTED = -1L;

    public BakedModel(int[] faces, long[] tintMask, float[] insets, float[] bounds, int metadata, int tintRow) {
        this(faces, tintMask, insets, bounds, metadata, tintRow, NO_VARIANTS);
    }

    public static BakedModel empty() {
        float[] insets = new float[FACE_COUNT];
        Arrays.fill(insets, EMPTY_INSET);
        return new BakedModel(new int[FACE_COUNT * FACE_TEXELS], untintedMask(), insets,
                new float[BOUNDS_LENGTH], 0, BiomeColours.NO_ROW);
    }

    public static BakedModel solid(int argb) {
        int[] faces = new int[FACE_COUNT * FACE_TEXELS];
        Arrays.fill(faces, argb);
        int metadata = ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, 0, 0);
        return new BakedModel(faces, untintedMask(), new float[FACE_COUNT], fullBounds(), metadata,
                BiomeColours.NO_ROW);
    }

    public static float[] fullBounds() {
        return new float[] {0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
    }

    public static long[] untintedMask() {
        return new long[TINT_MASK_WORDS];
    }

    public static long[] tintedMask() {
        long[] mask = new long[TINT_MASK_WORDS];
        Arrays.fill(mask, ALL_TINTED);
        return mask;
    }

    public static void mark(long[] mask, int index, boolean tinted) {
        long bit = 1L << index % Long.SIZE;
        if (tinted) {
            mask[index / Long.SIZE] |= bit;
        } else {
            mask[index / Long.SIZE] &= ~bit;
        }
    }

    public static boolean tinted(long[] mask, int index) {
        return (mask[index / Long.SIZE] & 1L << index % Long.SIZE) != 0;
    }

    public int argb(int face, int texel) {
        return faces[face * FACE_TEXELS + texel];
    }

    public BakedModel withVariants(int[] table) {
        return new BakedModel(faces, tintMask, insets, bounds, metadata, tintRow, table);
    }

    public boolean sameGeometry(BakedModel other) {
        return metadata == other.metadata
                && tintRow == other.tintRow
                && Arrays.equals(insets, other.insets)
                && Arrays.equals(bounds, other.bounds);
    }

    public int variantCount() {
        return variants.length / VARIANT_WORDS;
    }

    public boolean tinted(int face, int texel) {
        return tinted(tintMask, face * FACE_TEXELS + texel);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BakedModel model
                && metadata == model.metadata
                && tintRow == model.tintRow
                && Arrays.equals(faces, model.faces)
                && Arrays.equals(tintMask, model.tintMask)
                && Arrays.equals(insets, model.insets)
                && Arrays.equals(bounds, model.bounds)
                && Arrays.equals(variants, model.variants);
    }

    @Override
    public int hashCode() {
        int hash = Arrays.hashCode(faces);
        hash = 31 * hash + Arrays.hashCode(tintMask);
        hash = 31 * hash + Arrays.hashCode(insets);
        hash = 31 * hash + Arrays.hashCode(bounds);
        hash = 31 * hash + Arrays.hashCode(variants);
        hash = 31 * hash + metadata;
        return 31 * hash + tintRow;
    }

    @Override
    public String toString() {
        return "BakedModel[metadata=" + Integer.toHexString(metadata) + ", tintRow=" + tintRow + "]";
    }
}
