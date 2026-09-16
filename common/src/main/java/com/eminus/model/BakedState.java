package com.eminus.model;

import org.jspecify.annotations.Nullable;

public record BakedState(BakedModel block, @Nullable BakedModel fluid) {
}
