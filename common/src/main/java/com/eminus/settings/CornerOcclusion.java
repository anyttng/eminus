package com.eminus.settings;

import java.util.Optional;

public enum CornerOcclusion {
    EVERY_LEVEL("every_level"),
    FINEST_LEVEL("finest_level"),
    OFF("off");

    private final String key;

    CornerOcclusion(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<CornerOcclusion> fromKey(String key) {
        for (CornerOcclusion occlusion : values()) {
            if (occlusion.key.equals(key)) {
                return Optional.of(occlusion);
            }
        }

        return Optional.empty();
    }
}
