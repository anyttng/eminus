package com.eminus.model;

import com.eminus.cell.FaceMask;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.FluidState;

import org.jspecify.annotations.Nullable;

public final class FluidBaker {
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final int UP = Direction.UP.ordinal();

    private final FluidStateModelSet models;
    private final SolidSprites sprites;

    public FluidBaker(FluidStateModelSet models, SolidSprites sprites) {
        this.models = models;
        this.sprites = sprites;
    }

    public @Nullable BlockTintSource tintSource(FluidState fluid) {
        return models.get(fluid).tintSource();
    }

    public BakedModel bake(FluidState fluid, @Nullable Tint tint) {
        FluidModel model = models.get(fluid);
        int[] side = still(model.stillMaterial().sprite());
        int[] faces = new int[BakedModel.FACE_COUNT * BakedModel.FACE_TEXELS];

        for (int face = 0; face < BakedModel.FACE_COUNT; face++) {
            System.arraycopy(side, 0, faces, face * BakedModel.FACE_TEXELS, BakedModel.FACE_TEXELS);
        }

        boolean translucent = model.layer().translucent();
        long[] tintMask = tint == null ? BakedModel.untintedMask() : BakedModel.tintedMask();
        int tintRow = BiomeColours.NO_ROW;
        if (tint != null) {
            tint.apply(faces, tintMask);
            tintRow = tint.row();
        }

        int flags = (translucent ? ModelMetadata.TRANSLUCENT : 0)
                | (tintRow == BiomeColours.NO_ROW ? 0 : ModelMetadata.TINTED);
        int occluding = !translucent && opaque(side) ? FaceMask.ALL & ~FaceMask.UP : FaceMask.NONE;
        int metadata = ModelMetadata.pack(FaceMask.ALL, occluding, FaceMask.ALL & ~FaceMask.UP, 0, flags);

        float height = fluid.getOwnHeight();
        return new BakedModel(faces, tintMask, surfaceInsets(height), surfaceBounds(height), metadata, tintRow);
    }

    public static BakedModel submerged(BakedModel surface) {
        return new BakedModel(surface.faces(), surface.tintMask(), new float[BakedModel.FACE_COUNT],
                BakedModel.fullBounds(), surface.metadata(), surface.tintRow());
    }

    static float[] surfaceInsets(float height) {
        float[] insets = new float[BakedModel.FACE_COUNT];
        insets[UP] = 1.0F - height;
        return insets;
    }

    static float[] surfaceBounds(float height) {
        float[] bounds = BakedModel.fullBounds();
        bounds[BakedModel.MAX_Y] = height;
        return bounds;
    }

    private int[] still(TextureAtlasSprite sprite) {
        int[] side = new int[BakedModel.FACE_TEXELS];

        for (int row = 0; row < BakedModel.FACE_SIDE; row++) {
            float v = between(sprite.getV0(), sprite.getV1(), row);
            for (int column = 0; column < BakedModel.FACE_SIDE; column++) {
                float u = between(sprite.getU0(), sprite.getU1(), column);
                side[row * BakedModel.FACE_SIDE + column] = sprites.argb(sprite, u, v);
            }
        }

        return side;
    }

    private static float between(float start, float end, int step) {
        return start + (end - start) * (step + 0.5F) / BakedModel.FACE_SIDE;
    }

    private static boolean opaque(int[] side) {
        for (int texel : side) {
            if ((texel & ALPHA_MASK) != ALPHA_MASK) {
                return false;
            }
        }

        return true;
    }
}
