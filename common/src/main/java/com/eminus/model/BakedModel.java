package com.eminus.model;

import java.util.Arrays;

import com.eminus.cell.FaceMask;

import net.minecraft.core.Direction;

@SuppressWarnings("ArrayRecordComponent")
public record BakedModel(int[] faces, long[] tintMask, float[] insets, float[] slopes, float[] bounds, int metadata,
        int tintRow, int[] variants, int packId, FaceMip[] mips, int solidFaces, int shades) {
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
    public static final float BLOCK_BOTTOM = 0.0F;
    public static final float BLOCK_TOP = 1.0F;

    public static final int FIRST_SIDE_FACE = Direction.NORTH.get3DDataValue();
    public static final int SLOPES_PER_FACE = 2;
    public static final int SLOPES_LENGTH = FACE_COUNT * SLOPES_PER_FACE;

    public static final int VARIANT_WORDS = 2;
    public static final int MAX_VARIANT_REJECTIONS = 8;

    public static final int NO_PACK_ID = -1;

    public static final int SHADE_BITS = 3;
    public static final int OWN_SHADES = packShades(Direction.values());

    private static final int[] NO_VARIANTS = new int[0];

    private static final long ALL_TINTED = -1L;
    private static final FaceMip[] MEAN_MIPS = FaceMip.uniform(FaceMip.MEAN);
    private static final int FULL_ALPHA = 0xFF00_0000;

    public BakedModel(int[] faces, long[] tintMask, float[] insets, float[] slopes, float[] bounds, int metadata,
            int tintRow, FaceMip[] mips, int shades) {
        this(faces, tintMask, insets, slopes, bounds, metadata, tintRow, NO_VARIANTS, NO_PACK_ID, mips,
                solidFaces(faces, metadata, mips), shades);
    }

    public BakedModel(int[] faces, long[] tintMask, float[] insets, float[] bounds, int metadata, int tintRow,
            FaceMip[] mips) {
        this(faces, tintMask, insets, new float[SLOPES_LENGTH], bounds, metadata, tintRow, mips, OWN_SHADES);
    }

    public BakedModel(int[] faces, long[] tintMask, float[] insets, float[] bounds, int metadata, int tintRow) {
        this(faces, tintMask, insets, bounds, metadata, tintRow, MEAN_MIPS);
    }

    public static BakedModel empty() {
        float[] insets = new float[FACE_COUNT];
        Arrays.fill(insets, EMPTY_INSET);
        return new BakedModel(new int[FACE_COUNT * FACE_TEXELS], untintedMask(), insets,
                new float[BOUNDS_LENGTH], 0, BiomeColours.NO_ROW, MEAN_MIPS);
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

    public static int packShades(Direction[] shades) {
        int word = 0;
        for (int face = 0; face < FACE_COUNT; face++) {
            word |= shades[face].get3DDataValue() << (face * SHADE_BITS);
        }

        return word;
    }

    public int argb(int face, int texel) {
        return faces[face * FACE_TEXELS + texel];
    }

    public float slopeAlongWidth(int face) {
        return slopes[slopeIndex(face)];
    }

    public float slopeAlongHeight(int face) {
        return slopes[slopeIndex(face) + 1];
    }

    public static int slopeIndex(int face) {
        return face * SLOPES_PER_FACE;
    }

    public boolean fillsHeight() {
        return bounds[MIN_Y] <= BLOCK_BOTTOM && bounds[MAX_Y] >= BLOCK_TOP;
    }

    public BakedModel withVariants(int[] table, int solid) {
        return new BakedModel(faces, tintMask, insets, slopes, bounds, metadata, tintRow, table, packId, mips, solid,
                shades);
    }

    public BakedModel withMetadata(int word) {
        return new BakedModel(faces, tintMask, insets, slopes, bounds, word, tintRow, variants, packId, mips,
                solidFaces, shades);
    }

    public BakedModel withPackId(int id) {
        return new BakedModel(faces, tintMask, insets, slopes, bounds, metadata, tintRow, variants, id, mips,
                solidFaces, shades);
    }

    public BakedModel oneSided() {
        return withMetadata(metadata | ModelMetadata.ONE_SIDED);
    }

    public BakedModel inward(BakedModel fluid) {
        int word = ModelMetadata.pack(FaceMask.ALL, FaceMask.NONE, FaceMask.NONE, ModelMetadata.emission(metadata),
                (metadata & (ModelMetadata.TINTED | ModelMetadata.MOVED)) | ModelMetadata.FLUID
                        | ModelMetadata.ONE_SIDED | ModelMetadata.INWARD);
        return new BakedModel(faces, tintMask, fluid.insets, new float[SLOPES_LENGTH], fluid.bounds, word, tintRow,
                NO_VARIANTS, packId, mips, solidFaces, shades);
    }

    public boolean sameGeometry(BakedModel other) {
        return metadata == other.metadata
                && tintRow == other.tintRow
                && shades == other.shades
                && Arrays.equals(insets, other.insets)
                && Arrays.equals(slopes, other.slopes)
                && Arrays.equals(bounds, other.bounds);
    }

    public int variantCount() {
        return variants.length / VARIANT_WORDS;
    }

    private static int solidFaces(int[] faces, int metadata, FaceMip[] mips) {
        int present = ModelMetadata.present(metadata);
        if (ModelMetadata.has(metadata, ModelMetadata.OPAQUE)) {
            return present;
        }

        int solid = FaceMask.NONE;
        for (int face = 0; face < FACE_COUNT; face++) {
            if ((present & 1 << face) != 0 && opaqueAtEveryLevel(faces, face, mips[face])) {
                solid |= 1 << face;
            }
        }

        return solid;
    }

    private static boolean opaqueAtEveryLevel(int[] faces, int face, FaceMip mip) {
        int[] texels = Arrays.copyOfRange(faces, face * FACE_TEXELS, (face + 1) * FACE_TEXELS);
        for (int[] level : FaceMips.colourLevels(texels, FACE_SIDE, mip, false)) {
            for (int texel : level) {
                if ((texel & FULL_ALPHA) != FULL_ALPHA) {
                    return false;
                }
            }
        }

        return true;
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
                && Arrays.equals(slopes, model.slopes)
                && Arrays.equals(bounds, model.bounds)
                && Arrays.equals(variants, model.variants)
                && Arrays.equals(mips, model.mips)
                && packId == model.packId
                && shades == model.shades;
    }

    @Override
    public int hashCode() {
        int hash = Arrays.hashCode(faces);
        hash = 31 * hash + Arrays.hashCode(tintMask);
        hash = 31 * hash + Arrays.hashCode(insets);
        hash = 31 * hash + Arrays.hashCode(slopes);
        hash = 31 * hash + Arrays.hashCode(bounds);
        hash = 31 * hash + Arrays.hashCode(variants);
        hash = 31 * hash + Arrays.hashCode(mips);
        hash = 31 * hash + metadata;
        hash = 31 * hash + packId;
        hash = 31 * hash + shades;
        return 31 * hash + tintRow;
    }

    @Override
    public String toString() {
        return "BakedModel[metadata=" + Integer.toHexString(metadata) + ", tintRow=" + tintRow + ", packId=" + packId
                + "]";
    }
}
