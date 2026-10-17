package com.eminus.settings;

import java.util.Optional;

public enum LodQuality {
    ULTRA("ultra", 0, true, true),
    HIGH("high", 0, false, true),
    MEDIUM("medium", 1, false, true),
    LOW("low", 2, false, false),
    MINIMAL("minimal", 3, false, false);

    private static final int FULL_ATLAS_MIP = 0;
    private static final int ECONOMY_ATLAS_MIP = 1;

    private final String key;
    private final int storedLevel;
    private final boolean fullDetail;
    private final boolean fullAtlas;

    LodQuality(String key, int storedLevel, boolean fullDetail, boolean fullAtlas) {
        this.key = key;
        this.storedLevel = storedLevel;
        this.fullDetail = fullDetail;
        this.fullAtlas = fullAtlas;
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

    public int atlasFirstMip() {
        return fullAtlas ? FULL_ATLAS_MIP : ECONOMY_ATLAS_MIP;
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
