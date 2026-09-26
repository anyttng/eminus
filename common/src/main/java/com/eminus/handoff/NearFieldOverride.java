package com.eminus.handoff;

public final class NearFieldOverride {
    private static boolean applied;

    public static void apply() {
        applied = true;
    }

    public static void skip() {
        applied = false;
    }

    // The last extract's decision, because work inside the next extract runs before the override is written again.
    public static boolean applied() {
        return applied;
    }

    private NearFieldOverride() {
    }
}
