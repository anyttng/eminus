package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import com.eminus.model.port.MipBlend;
import com.eminus.model.port.MipStrategy;

import org.junit.jupiter.api.Test;

class FaceMipsTest {
    private static final int FACE_SIDE = 16;
    private static final int WHITE = 0xFFFF_FFFF;
    private static final int BLACK = 0xFF00_0000;
    private static final int CLEAR_WHITE = 0x00FF_FFFF;
    private static final int FAINT_WHITE = 0x40FF_FFFF;
    private static final int GAMMA_GRAY = 0xFFBA_BABA;
    private static final int GAMMA_HALF_WHITE = 0xBAFF_FFFF;
    private static final int DRAWN_HALF_GRAY = 0xBABA_BABA;
    private static final int DRAWN_QUARTER_GRAY = 0x0087_8787;
    private static final FaceMip MEAN = FaceMip.MEAN;
    private static final FaceMip CUTOUT = new FaceMip(MipStrategy.CUTOUT);
    private static final MipBlend VANILLA = VanillaMipBlend.BLEND;

    @Test
    void aFaceHasOneLevelPerHalvingDownToASingleTexel() {
        int[][] levels = FaceMips.colorLevels(new int[FACE_SIDE * FACE_SIDE], FACE_SIDE, MEAN, false, VANILLA);

        assertArrayEquals(new int[] {256, 64, 16, 4, 1},
                Arrays.stream(levels).mapToInt(level -> level.length).toArray());
    }

    @Test
    void oneColorSurvivesEveryLevel() {
        int[] face = new int[FACE_SIDE * FACE_SIDE];
        Arrays.fill(face, WHITE);

        for (int[] level : FaceMips.colorLevels(face, FACE_SIDE, MEAN, false, VANILLA)) {
            for (int texel : level) {
                assertEquals(WHITE, texel);
            }
        }
    }

    @Test
    void theSourceFaceIsTheFirstLevelItself() {
        int[] face = new int[4];

        assertEquals(face, FaceMips.colorLevels(face, 2, MEAN, false, VANILLA)[0]);
    }

    @Test
    void aMeanGroupAveragesEveryChannelInGamma22() {
        assertArrayEquals(new int[] {GAMMA_GRAY},
                FaceMips.colorLevels(new int[] {WHITE, BLACK, WHITE, BLACK}, 2, MEAN, false, VANILLA)[1]);
        assertArrayEquals(new int[] {GAMMA_HALF_WHITE},
                FaceMips.colorLevels(new int[] {WHITE, WHITE, CLEAR_WHITE, CLEAR_WHITE}, 2, MEAN, false,
                        VANILLA)[1]);
    }

    @Test
    void aCutoutGroupCountsOnlyItsDrawnTexelsOverAllFour() {
        int[][] levels = FaceMips.colorLevels(new int[] {WHITE, WHITE, CLEAR_WHITE, CLEAR_WHITE}, 2,
                CUTOUT, false, VANILLA);

        assertArrayEquals(new int[] {DRAWN_HALF_GRAY}, levels[1]);
    }

    @Test
    void aCutoutGroupBelowTheAlphaCutoffComesOutClear() {
        int[][] levels = FaceMips.colorLevels(new int[] {FAINT_WHITE, CLEAR_WHITE, CLEAR_WHITE, CLEAR_WHITE}, 2,
                CUTOUT, false, VANILLA);

        assertArrayEquals(new int[] {DRAWN_QUARTER_GRAY}, levels[1]);
    }

    @Test
    void aForcedOpaqueFaceBlendsByItsHolesThenDrawsEveryLevelOpaque() {
        int[][] levels = FaceMips.colorLevels(new int[] {WHITE, WHITE, CLEAR_WHITE, CLEAR_WHITE}, 2,
                CUTOUT, true, VANILLA);

        assertArrayEquals(new int[] {WHITE, WHITE, WHITE, WHITE}, levels[0]);
        assertArrayEquals(new int[] {Argb.opaque(DRAWN_HALF_GRAY)}, levels[1]);
    }
}
