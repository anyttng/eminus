package com.eminus.render.far;

import com.eminus.settings.FarDistance;

public record CompositeFog(float fogStart, float fogEnd, float fadeStart, float fadeEnd, boolean skip) {
    public static final float NONE = Float.MAX_VALUE;
    public static final float FADE_BAND_BLOCKS = FarDistance.BLOCKS_PER_TOP_LEVEL_CELL;

    public static CompositeFog of(boolean fog, boolean fade, float environmentalStart, float environmentalEnd,
            float nearBlocks, int farCells) {
        float farBlocks = (float) farCells * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL;

        return new CompositeFog(
                fog ? stretchedStart(environmentalStart, environmentalEnd, nearBlocks, farBlocks) : NONE,
                fog ? stretchedEnd(environmentalEnd, nearBlocks, farBlocks) : NONE,
                fade ? farBlocks - FADE_BAND_BLOCKS : NONE,
                fade ? farBlocks : NONE,
                skipped(environmentalEnd, nearBlocks));
    }

    public static boolean skipped(float environmentalEnd, float nearBlocks) {
        return environmentalEnd <= nearBlocks;
    }

    // The line through the game's fog value at the near edge and full fog at the far render distance.
    private static float stretchedStart(float gameStart, float gameEnd, float nearBlocks, float farBlocks) {
        if (farBlocks <= nearBlocks || gameEnd <= nearBlocks) {
            return gameStart;
        }

        float atNear = Math.clamp((nearBlocks - gameStart) / (gameEnd - gameStart), 0.0F, 1.0F);
        return nearBlocks - atNear * (farBlocks - nearBlocks) / (1.0F - atNear);
    }

    private static float stretchedEnd(float gameEnd, float nearBlocks, float farBlocks) {
        return farBlocks <= nearBlocks || gameEnd <= nearBlocks ? gameEnd : farBlocks;
    }
}
