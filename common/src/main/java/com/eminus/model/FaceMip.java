package com.eminus.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.eminus.model.port.MipStrategy;
import com.eminus.model.port.Sprite;

import org.jspecify.annotations.Nullable;

public record FaceMip(MipStrategy strategy, float alphaCutoffBias) {
    public static final FaceMip MEAN = new FaceMip(MipStrategy.MEAN, 0.0F);

    public static FaceMip of(Sprite sprite) {
        return new FaceMip(sprite.mipStrategy(), sprite.alphaCutoffBias());
    }

    public static FaceMip[] uniform(FaceMip mip) {
        FaceMip[] mips = new FaceMip[BakedModel.FACE_COUNT];
        Arrays.fill(mips, mip);
        return mips;
    }

    static FaceMip drawnBy(@Nullable Sprite[] painters) {
        Map<Sprite, Integer> covered = new HashMap<>();
        Sprite widest = null;
        int widestTexels = 0;

        for (Sprite painter : painters) {
            if (painter == null) {
                continue;
            }

            if (painter.mipStrategy() == MipStrategy.DARK_CUTOUT) {
                return of(painter);
            }

            int texels = covered.merge(painter, 1, Integer::sum);
            if (texels > widestTexels) {
                widest = painter;
                widestTexels = texels;
            }
        }

        return widest == null ? MEAN : of(widest);
    }
}
