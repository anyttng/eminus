package com.eminus.settings;

import java.util.Optional;

public enum FogMode {
    FOG_AND_FADE("fog_and_fade", true, true),
    FOG("fog", true, false),
    FADE("fade", false, true),
    OFF("off", false, false);

    private final String key;
    private final boolean fogs;
    private final boolean fades;

    FogMode(String key, boolean fogs, boolean fades) {
        this.key = key;
        this.fogs = fogs;
        this.fades = fades;
    }

    public String key() {
        return key;
    }

    public boolean fogs() {
        return fogs;
    }

    public boolean fades() {
        return fades;
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
