package com.eminus.client.render.far;

import org.jspecify.annotations.Nullable;

public record FarStart(@Nullable FarRenderer renderer, @Nullable String refusal) {

    static FarStart started(FarRenderer renderer) {
        return new FarStart(renderer, null);
    }

    static FarStart refused(String refusal) {
        return new FarStart(null, refusal);
    }
}
