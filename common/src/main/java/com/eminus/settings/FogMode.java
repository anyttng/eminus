package com.eminus.settings;

import java.util.Optional;

public enum FogMode {
    FOG_AND_FADE("fog_and_fade"),
    FOG("fog"),
    FADE("fade"),
    OFF("off");

    private final String key;

    FogMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<FogMode> fromKey(String key) {
        for (FogMode mode : values()) {
            if (mode.key.equals(key)) {
                return Optional.of(mode);
            }
        }

        return Optional.empty();
    }
}
