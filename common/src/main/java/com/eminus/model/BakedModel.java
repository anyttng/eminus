package com.eminus.model;

import java.util.Arrays;

import com.eminus.cell.FaceMask;

import net.minecraft.client.color.block.BlockTintSource;

public record BakedModel(int[] faces, float[] insets, float[] bounds, int metadata, BlockTintSource tint) {
    public static final int FACE_COUNT = 6;
    public static final int FACE_SIDE = 16;
    public static final int FACE_TEXELS = FACE_SIDE * FACE_SIDE;

    public static final int BOUNDS_LENGTH = 6;
    public static final int MIN_X = 0;
    public static final int MIN_Y = 1;
    public static final int MIN_Z = 2;
    public static final int MAX_X = 3;
    public static final int MAX_Y = 4;
    public static final int MAX_Z = 5;

    public static final float EMPTY_INSET = 1.0F;

    public static BakedModel empty() {
        float[] insets = new float[FACE_COUNT];
        Arrays.fill(insets, EMPTY_INSET);
        return new BakedModel(
                new int[FACE_COUNT * FACE_TEXELS], insets, new float[BOUNDS_LENGTH], 0, null);
    }

    public static BakedModel solid(int argb) {
        int[] faces = new int[FACE_COUNT * FACE_TEXELS];
        Arrays.fill(faces, argb);
        int metadata = ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, 0, 0);
        return new BakedModel(faces, new float[FACE_COUNT], fullBounds(), metadata, null);
    }

    public static float[] fullBounds() {
        return new float[] {0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
    }

    public int argb(int face, int texel) {
        return faces[face * FACE_TEXELS + texel];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BakedModel model
                && metadata == model.metadata
                && tint == model.tint
                && Arrays.equals(faces, model.faces)
                && Arrays.equals(insets, model.insets)
                && Arrays.equals(bounds, model.bounds);
    }

    @Override
    public int hashCode() {
        int hash = Arrays.hashCode(faces);
        hash = 31 * hash + Arrays.hashCode(insets);
        hash = 31 * hash + Arrays.hashCode(bounds);
        hash = 31 * hash + metadata;
        return 31 * hash + System.identityHashCode(tint);
    }

    @Override
    public String toString() {
        return "BakedModel[metadata=" + Integer.toHexString(metadata) + ", tinted=" + (tint != null) + "]";
    }
}
