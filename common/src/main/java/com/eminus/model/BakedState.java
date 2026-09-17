package com.eminus.model;

import java.util.List;

import net.minecraft.util.random.Weighted;

import org.jspecify.annotations.Nullable;

public record BakedState(BakedModel block, @Nullable BakedModel fluid, @Nullable BakedModel submerged,
        List<Weighted<BakedModel>> variants, boolean positional) {
    public BakedState(BakedModel block, @Nullable BakedModel fluid) {
        this(block, fluid, null);
    }

    public BakedState(BakedModel block, @Nullable BakedModel fluid, @Nullable BakedModel submerged) {
        this(block, fluid, submerged, List.of(), false);
    }
}
