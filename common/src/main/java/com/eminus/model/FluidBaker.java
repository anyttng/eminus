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
    private static final float FULL_HEIGHT = 1.0F;

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

        int flags = ModelMetadata.FLUID
                | (translucent ? ModelMetadata.TRANSLUCENT : 0)
                | (tintRow == BiomeColours.NO_ROW ? 0 : ModelMetadata.TINTED);
        float height = fluid.getOwnHeight();
        float[] bounds = surfaceBounds(height);
        int metadata = ModelMetadata.pack(FaceMask.ALL, occluding(bounds, translucent || !opaque(side)),
                occludable(bounds), 0, flags);

        return new BakedModel(faces, tintMask, surfaceInsets(height), bounds, metadata, tintRow);
    }

    public static BakedModel submerged(BakedModel surface) {
        int word = surface.metadata();
        float[] bounds = BakedModel.fullBounds();
        boolean seeThrough = ModelMetadata.has(word, ModelMetadata.TRANSLUCENT) || !opaque(surface.faces());
        int metadata = ModelMetadata.pack(ModelMetadata.present(word), occluding(bounds, seeThrough),
                occludable(bounds), ModelMetadata.emission(word), word & ModelMetadata.FLAGS);

        return new BakedModel(surface.faces(), surface.tintMask(), new float[BakedModel.FACE_COUNT], bounds,
                metadata, surface.tintRow());
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

    static int occluding(float[] bounds, boolean seeThrough) {
        if (seeThrough) {
            return FaceMask.NONE;
        }

        return reachesTop(bounds) ? FaceMask.ALL : FaceMask.DOWN;
    }

    static int occludable(float[] bounds) {
        return reachesTop(bounds) ? FaceMask.ALL : FaceMask.ALL & ~FaceMask.UP;
    }

    private static boolean reachesTop(float[] bounds) {
        return bounds[BakedModel.MAX_Y] >= FULL_HEIGHT;
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

    private static boolean opaque(int[] texels) {
        for (int texel : texels) {
            if ((texel & ALPHA_MASK) != ALPHA_MASK) {
                return false;
            }
        }

        return true;
    }
}
