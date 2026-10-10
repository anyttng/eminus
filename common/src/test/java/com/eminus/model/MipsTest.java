package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class MipsTest {
    private static final int WHITE = 0xFFFF_FFFF;
    private static final int BLACK = 0xFF00_0000;
    private static final int GREY = 0xFF80_8080;

    @Test
    void aMaskGroupAveragesArithmetically() {
        int[] quad = {WHITE, BLACK, WHITE, BLACK};

        int[][] levels = Mips.maskChain(quad, 2);

        assertArrayEquals(new int[] {GREY}, levels[1]);
    }
}
