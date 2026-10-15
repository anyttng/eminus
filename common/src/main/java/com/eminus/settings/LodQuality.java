package com.eminus.settings;

import java.util.Optional;

public enum LodQuality {
    FULL("full", 0, true),
    HIGH("high", 0, false),
    MEDIUM("medium", 1, false),
    LOW("low", 2, false),
    MINIMAL("minimal", 3, false);

    private final String key;
    private final int storedLevel;
    private final boolean fullDetail;

    LodQuality(String key, int storedLevel, boolean fullDetail) {
        this.key = key;
        this.storedLevel = storedLevel;
        this.fullDetail = fullDetail;
    }

    public String key() {
        return key;
    }

    public int storedLevel() {
        return storedLevel;
    }

    public boolean cutoutLeaves(boolean seeThroughLeaves) {
        return seeThroughLeaves && fullDetail;
    }

    public boolean drawsBlades() {
        return fullDetail;
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
            if (quality.storedLevel >= level) {
                return quality;
            }
        }

        return MINIMAL;
    }
}
