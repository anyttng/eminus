package com.eminus.settings;

import java.util.Optional;

public enum ShaderPackLod {
    DEFAULT("default"),
    DISTANT_HORIZONS("distant_horizons");

    private final String key;

    ShaderPackLod(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<ShaderPackLod> fromKey(String key) {
        for (ShaderPackLod lod : values()) {
            if (lod.key.equals(key)) {
                return Optional.of(lod);
            }
        }

        return Optional.empty();
    }
}
