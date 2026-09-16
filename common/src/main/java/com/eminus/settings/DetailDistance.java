package com.eminus.settings;

import java.util.Optional;

public enum DetailDistance {
    ULTRA("ultra", 16),
    HIGH("high", 32),
    MEDIUM("medium", 64),
    LOW("low", 128),
    MINIMAL("minimal", 256);

    private final String key;
    private final int pixels;

    DetailDistance(String key, int pixels) {
        this.key = key;
        this.pixels = pixels;
    }

    public String key() {
        return key;
    }

    public int pixels() {
        return pixels;
    }

    public static Optional<DetailDistance> fromKey(String key) {
        for (DetailDistance distance : values()) {
            if (distance.key.equals(key)) {
                return Optional.of(distance);
            }
        }

        return Optional.empty();
    }
}
