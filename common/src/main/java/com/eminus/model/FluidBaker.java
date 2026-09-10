package com.eminus.model;

import com.eminus.cell.FaceMask;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.FluidState;

public final class FluidBaker {
    private static final int ALPHA_MASK = 0xFF00_0000;

    private final FluidStateModelSet models;
    private final SolidSprites sprites;

    public FluidBaker(FluidStateModelSet models, SolidSprites sprites) {
        this.models = models;
        this.sprites = sprites;
    }

    public BakedModel bake(FluidState fluid) {
        FluidModel model = models.get(fluid);
        int[] side = still(model.stillMaterial().sprite());
        int[] faces = new int[BakedModel.FACE_COUNT * BakedModel.FACE_TEXELS];

        for (int face = 0; face < BakedModel.FACE_COUNT; face++) {
            System.arraycopy(side, 0, faces, face * BakedModel.FACE_TEXELS, BakedModel.FACE_TEXELS);
        }

        boolean translucent = model.layer().translucent();
        BlockTintSource tint = model.tintSource();
        int flags = (translucent ? ModelMetadata.TRANSLUCENT : 0) | (tint == null ? 0 : ModelMetadata.TINTED);
        int occluding = !translucent && opaque(side) ? FaceMask.ALL : FaceMask.NONE;
        int metadata = ModelMetadata.pack(FaceMask.ALL, occluding, FaceMask.ALL, flags);

        return new BakedModel(
                faces, new float[BakedModel.FACE_COUNT], BakedModel.fullBounds(), metadata, tint);
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
