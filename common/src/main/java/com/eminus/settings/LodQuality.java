package com.eminus.settings;

import java.util.Optional;

public enum LodQuality {
    FULL("full", 0, false),
    HIGH("high", 0, true),
    MEDIUM("medium", 1, false),
    LOW("low", 2, false),
    MINIMAL("minimal", 3, false);

    private final String key;
    private final int storedLevel;
    private final boolean economy;

    LodQuality(String key, int storedLevel, boolean economy) {
        this.key = key;
        this.storedLevel = storedLevel;
        this.economy = economy;
    }

    public String key() {
        return key;
    }

    public int storedLevel() {
        return storedLevel;
    }

    public boolean economy() {
        return economy;
    }

    public static Optional<LodQuality> fromKey(String key) {
        for (LodQuality quality : values()) {
            if (quality.key.equals(key)) {
                return Optional.of(quality);
            }
        }

        return Optional.empty();
    }

    public static LodQuality fromLowestStoredLevel(int level) {
        for (LodQuality quality : values()) {
            if (!quality.economy && quality.storedLevel >= level) {
                return quality;
            }
        }

        return MINIMAL;
    }
}
