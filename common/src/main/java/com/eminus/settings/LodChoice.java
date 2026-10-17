package com.eminus.settings;

import java.util.Optional;

public enum LodChoice {
    ULTRA("ultra", true, 0, LeafMode.SEE_THROUGH, true, true),
    HIGH("high", true, 0, LeafMode.OPAQUE, false, true),
    MEDIUM("medium", true, 1, LeafMode.OPAQUE, false, true),
    LOW("low", true, 2, LeafMode.OPAQUE, false, false),
    MINIMAL("minimal", true, 3, LeafMode.OPAQUE, false, false),
    CUSTOM("custom", false, 0, LeafMode.OPAQUE, false, false);

    private static final LodChoice[] PRESETS_FINEST_FIRST = {ULTRA, HIGH, MEDIUM, LOW, MINIMAL};
    private static final CornerOcclusion PRESET_CORNER_OCCLUSION = CornerOcclusion.FINEST_LEVEL;

    private final String key;
    private final boolean preset;
    private final int storedLevel;
    private final LeafMode leaves;
    private final boolean drawsBlades;
    private final boolean fullTextures;

    LodChoice(String key, boolean preset, int storedLevel, LeafMode leaves, boolean drawsBlades,
            boolean fullTextures) {
        this.key = key;
        this.preset = preset;
        this.storedLevel = storedLevel;
        this.leaves = leaves;
        this.drawsBlades = drawsBlades;
        this.fullTextures = fullTextures;
    }

    public String key() {
        return key;
    }

    public Optional<LodQuality> presetQuality() {
        return preset
                ? Optional.of(new LodQuality(storedLevel, leaves, drawsBlades, fullTextures,
                        PRESET_CORNER_OCCLUSION))
                : Optional.empty();
    }

    public LodQuality quality(LodQuality custom) {
        return presetQuality().orElse(custom);
    }

    public static Optional<LodChoice> fromKey(String key) {
        for (LodChoice choice : values()) {
            if (choice.key.equals(key)) {
                return Optional.of(choice);
            }
        }

        return Optional.empty();
    }

    public static LodChoice fromLowestStoredLevel(int level) {
        for (LodChoice choice : PRESETS_FINEST_FIRST) {
            if (choice.storedLevel >= level) {
                return choice;
            }
        }

        return MINIMAL;
    }
}
