package com.eminus.model.port;

import org.jspecify.annotations.Nullable;

public record FluidModel(Sprite still, boolean translucent, @Nullable TintSource tint) {
}
