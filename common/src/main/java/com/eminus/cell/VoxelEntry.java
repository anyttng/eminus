package com.eminus.cell;

public final class VoxelEntry {
    public static final int AIR_STATE_ID = 0;
    public static final int UNKNOWN_BIOME = 0xFFFF;
    public static final int MAX_BIOME_ID = UNKNOWN_BIOME - 1;
    public static final int MAX_LIGHT = 15;

    private static final int BIOME_SHIFT = 32;
    private static final int LIGHT_SHIFT = 48;
    private static final int SKY_LIGHT_SHIFT = 4;
    private static final long STATE_MASK = 0xFFFF_FFFFL;
    private static final long BIOME_MASK = 0xFFFFL;
    private static final long LIGHT_MASK = 0xFFL;
    private static final int NIBBLE_MASK = 0xF;

    public static final long AIR = pack(AIR_STATE_ID, UNKNOWN_BIOME, light(MAX_LIGHT, 0));

    public static long pack(int stateId, int biomeId, int light) {
        return (stateId & STATE_MASK)
                | ((biomeId & BIOME_MASK) << BIOME_SHIFT)
                | ((light & LIGHT_MASK) << LIGHT_SHIFT);
    }

    public static int light(int skyLight, int blockLight) {
        return ((skyLight & NIBBLE_MASK) << SKY_LIGHT_SHIFT) | (blockLight & NIBBLE_MASK);
    }

    public static int state(long entry) {
        return (int) (entry & STATE_MASK);
    }

    public static int biome(long entry) {
        return (int) ((entry >>> BIOME_SHIFT) & BIOME_MASK);
    }

    public static boolean hasBiome(long entry) {
        return biome(entry) != UNKNOWN_BIOME;
    }

    public static int light(long entry) {
        return (int) ((entry >>> LIGHT_SHIFT) & LIGHT_MASK);
    }

    public static int skyLight(long entry) {
        return (light(entry) >>> SKY_LIGHT_SHIFT) & NIBBLE_MASK;
    }

    public static int blockLight(long entry) {
        return light(entry) & NIBBLE_MASK;
    }

    public static boolean isAir(long entry) {
        return state(entry) == AIR_STATE_ID;
    }

    private VoxelEntry() {
    }
}
