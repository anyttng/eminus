package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import com.eminus.model.port.MipStrategy;

import org.junit.jupiter.api.Test;

class FaceMipsTest {
    private static final int FACE_SIDE = 16;
    private static final int WHITE = 0xFFFF_FFFF;
    private static final int BLACK = 0xFF00_0000;
    private static final int EMPTY = 0x0000_0000;
    private static final int LINEAR_GREY = 0xFFBB_BBBB;
    private static final int FLOORED_HALF_GREY = 0x7FBB_BBBB;
    private static final int MID_GREY = 0xFF80_8080;
    private static final int DARK_GREY = 0xFF40_4040;
    private static final int DARK_FILL = 0x0030_3030;
    private static final int WHITE_DARK_FILL = 0xFFBF_BFBF;
    private static final int LIFTED_HALF_WHITE = 0x85FF_FFFF;
    private static final int RGB_MASK = 0x00FF_FFFF;
    private static final int DARKENED_HALF_WHITE_RGB = 0x00BB_BBBB;
    private static final FaceMip MEAN = FaceMip.MEAN;
    private static final FaceMip CUTOUT = new FaceMip(MipStrategy.CUTOUT, 0.0F);
    private static final FaceMip DARK_CUTOUT = new FaceMip(MipStrategy.DARK_CUTOUT, 0.0F);

    @Test
    void aFaceHasOneLevelPerHalvingDownToASingleTexel() {
        int[][] levels = FaceMips.colourLevels(new int[FACE_SIDE * FACE_SIDE], FACE_SIDE, MEAN, false);

        assertArrayEquals(new int[] {256, 64, 16, 4, 1},
                Arrays.stream(levels).mapToInt(level -> level.length).toArray());
    }

    @Test
    void oneColourSurvivesEveryLevel() {
        int[] face = new int[FACE_SIDE * FACE_SIDE];
        Arrays.fill(face, WHITE);

        for (int[] level : FaceMips.colourLevels(face, FACE_SIDE, MEAN, false)) {
            for (int texel : level) {
                assertEquals(WHITE, texel);
            }
        }
    }

    @Test
    void theSourceFaceIsTheFirstLevelItself() {
        int[] face = new int[4];

        assertEquals(face, FaceMips.colourLevels(face, 2, MEAN, false)[0]);
    }

    @Test
    void aMeanGroupAveragesInLinearLight() {
        int[][] levels = FaceMips.colourLevels(new int[] {WHITE, BLACK, WHITE, BLACK}, 2, MEAN, false);

        assertArrayEquals(new int[] {LINEAR_GREY}, levels[1]);
    }

    @Test
    void aMeanGroupFloorsItsAlphaAsTheGameDoes() {
        int[][] levels = FaceMips.colourLevels(new int[] {EMPTY, EMPTY, WHITE, WHITE}, 2, MEAN, false);

        assertArrayEquals(new int[] {FLOORED_HALF_GREY}, levels[1]);
    }

    @Test
    void aDarkCutoutFaceFillsItsEmptyTexelsWithThreeQuartersOfItsDarkestColour() {
        int[][] levels = FaceMips.colourLevels(new int[] {MID_GREY, DARK_GREY, EMPTY, EMPTY}, 2, DARK_CUTOUT, false);

        assertArrayEquals(new int[] {MID_GREY, DARK_GREY, DARK_FILL, DARK_FILL}, levels[0]);
    }

    @Test
    void aDarkCutoutGroupCountsItsEmptyTexelsAsBlack() {
        int[][] levels = FaceMips.colourLevels(new int[] {WHITE, WHITE, EMPTY, EMPTY}, 2, DARK_CUTOUT, false);

        assertEquals(DARKENED_HALF_WHITE_RGB, levels[1][0] & RGB_MASK);
    }

    @Test
    void theSingleTexelLevelOfACutoutTakesOnlyTheAlphaLift() {
        int[][] levels = FaceMips.colourLevels(new int[] {WHITE, WHITE, EMPTY, EMPTY}, 2, CUTOUT, false);

        assertArrayEquals(new int[] {LIFTED_HALF_WHITE}, levels[1]);
    }

    @Test
    void aForcedOpaqueFaceBlendsByItsHolesThenDrawsEveryLevelOpaque() {
        int[][] levels = FaceMips.colourLevels(new int[] {WHITE, WHITE, EMPTY, EMPTY}, 2, DARK_CUTOUT, true);

        assertArrayEquals(new int[] {WHITE, WHITE, WHITE_DARK_FILL, WHITE_DARK_FILL}, levels[0]);
        assertArrayEquals(new int[] {LINEAR_GREY}, levels[1]);
    }
}
