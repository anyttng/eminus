package com.eminus.render.far;

import com.eminus.settings.FarDistance;
import com.eminus.settings.FogMode;

public record CompositeFog(float fogStart, float fogEnd, float fadeStart, float fadeEnd, boolean skip) {
    public static final float NONE = Float.MAX_VALUE;
    public static final float FADE_BAND_BLOCKS = FarDistance.BLOCKS_PER_TOP_LEVEL_CELL;

    public static CompositeFog of(FogMode mode, float environmentalStart, float environmentalEnd, float nearBlocks,
            int farCells) {
        float farBlocks = (float) farCells * FarDistance.BLOCKS_PER_TOP_LEVEL_CELL;
        boolean skip = skipped(mode, environmentalEnd, nearBlocks);

        return new CompositeFog(
                mode.fogs() ? stretchedStart(environmentalStart, environmentalEnd, nearBlocks, farBlocks) : NONE,
                mode.fogs() ? stretchedEnd(environmentalEnd, nearBlocks, farBlocks) : NONE,
                mode.fades() ? farBlocks - FADE_BAND_BLOCKS : NONE,
                mode.fades() ? farBlocks : NONE,
                skip);
    }

    public static boolean skipped(FogMode mode, float environmentalEnd, float nearBlocks) {
        return mode.fogs() && environmentalEnd <= nearBlocks;
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
