package com.eminus.settings;

import java.util.Optional;

public enum LodChoice {
    ULTRA("ultra", true, LeafMode.SEE_THROUGH, true, true, CornerOcclusion.FINEST_LEVEL),
    HIGH("high", true, LeafMode.OPAQUE, false, true, CornerOcclusion.FINEST_LEVEL),
    MEDIUM("medium", true, LeafMode.OPAQUE, false, false, CornerOcclusion.FINEST_LEVEL),
    LOW("low", true, LeafMode.OPAQUE, false, false, CornerOcclusion.OFF),
    CUSTOM("custom", false, LeafMode.OPAQUE, false, false, CornerOcclusion.OFF);

    private static final int PRESET_STORED_LEVEL = LodQuality.MIN_STORED_LEVEL;
    private static final int FORMER_ULTRA_LEVEL = 0;
    private static final int FORMER_MEDIUM_LEVEL = 1;

    private final String key;
    private final boolean preset;
    private final LeafMode leaves;
    private final boolean drawsBlades;
    private final boolean fullTextures;
    private final CornerOcclusion cornerOcclusion;

    LodChoice(String key, boolean preset, LeafMode leaves, boolean drawsBlades, boolean fullTextures,
            CornerOcclusion cornerOcclusion) {
        this.key = key;
        this.preset = preset;
        this.leaves = leaves;
        this.drawsBlades = drawsBlades;
        this.fullTextures = fullTextures;
        this.cornerOcclusion = cornerOcclusion;
    }

    public String key() {
        return key;
    }

    public Optional<LodQuality> presetQuality() {
        return preset
                ? Optional.of(new LodQuality(PRESET_STORED_LEVEL, leaves, drawsBlades, fullTextures, cornerOcclusion))
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
        return switch (level) {
            case FORMER_ULTRA_LEVEL -> ULTRA;
            case FORMER_MEDIUM_LEVEL -> MEDIUM;
            default -> LOW;
        };
    }
}
