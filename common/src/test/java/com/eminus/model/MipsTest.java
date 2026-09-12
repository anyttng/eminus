package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class MipsTest {
    private static final int FACE_SIDE = 16;
    private static final int WHITE = 0xFFFF_FFFF;
    private static final int BLACK = 0xFF00_0000;
    private static final int GREY = 0xFF80_8080;
    private static final int LINEAR_GREY = 0xFFBB_BBBB;
    private static final int CLEAR_WHITE = 0x00FF_FFFF;
    private static final int HALF_CLEAR_WHITE = 0x80FF_FFFF;

    @Test
    void aFaceHasOneLevelPerHalvingDownToASingleTexel() {
        int[][] levels = Mips.colourChain(new int[FACE_SIDE * FACE_SIDE], FACE_SIDE);

        assertEquals(5, levels.length);
        assertArrayEquals(new int[] {256, 64, 16, 4, 1},
                Arrays.stream(levels).mapToInt(level -> level.length).toArray());
    }

    @Test
    void oneColourSurvivesEveryLevel() {
        int[] face = new int[FACE_SIDE * FACE_SIDE];
        Arrays.fill(face, WHITE);

        for (int[] level : Mips.colourChain(face, FACE_SIDE)) {
            for (int texel : level) {
                assertEquals(WHITE, texel);
            }
        }
    }

    @Test
    void aColourGroupAveragesInLinearLight() {
        int[] quad = {WHITE, BLACK, WHITE, BLACK};

        int[][] levels = Mips.colourChain(quad, 2);

        assertEquals(2, levels.length);
        assertArrayEquals(new int[] {LINEAR_GREY}, levels[1]);
    }

    @Test
    void colourAlphaKeepsTheRoundedArithmeticMean() {
        int[] quad = {CLEAR_WHITE, CLEAR_WHITE, WHITE, WHITE};

        int[][] levels = Mips.colourChain(quad, 2);

        assertArrayEquals(new int[] {HALF_CLEAR_WHITE}, levels[1]);
    }

    @Test
    void aMaskGroupAveragesArithmetically() {
        int[] quad = {WHITE, BLACK, WHITE, BLACK};

        int[][] levels = Mips.maskChain(quad, 2);

        assertArrayEquals(new int[] {GREY}, levels[1]);
    }

    @Test
    void theSourceFaceIsTheFirstLevelItself() {
        int[] face = new int[4];

        int[][] levels = Mips.colourChain(face, 2);

        assertEquals(face, levels[0]);
    }
}
