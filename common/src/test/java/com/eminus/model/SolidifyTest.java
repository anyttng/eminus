package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SolidifyTest {
    private static final int TRANSPARENT = 0x0000_0000;
    private static final int RED = 0xFFFF_0000;
    private static final int GREEN = 0xFF00_FF00;
    private static final int BLUE = 0xFF00_00FF;
    private static final int TRANSPARENT_RED = 0x00FF_0000;
    private static final int TRANSPARENT_GREEN = 0x0000_FF00;
    private static final int TRANSPARENT_BLUE = 0x0000_00FF;

    @Test
    void oneDrawnPixelColoursEveryTransparentPixelAndKeepsTheirAlpha() {
        int[] image = new int[16];
        image[0] = GREEN;

        Solidify.apply(image, 4, 4);

        assertEquals(GREEN, image[0]);
        for (int index = 1; index < image.length; index++) {
            assertEquals(TRANSPARENT_GREEN, image[index], "texel " + index);
        }
    }

    @Test
    void aTransparentPixelTakesTheNearerOfTwoDrawnColours() {
        int[] row = {RED, TRANSPARENT, TRANSPARENT, TRANSPARENT, TRANSPARENT, BLUE};

        Solidify.apply(row, 6, 1);

        assertArrayEquals(
                new int[] {RED, TRANSPARENT_RED, TRANSPARENT_RED, TRANSPARENT_BLUE, TRANSPARENT_BLUE, BLUE}, row);
    }

    @Test
    void anImageWithoutADrawnPixelIsLeftAlone() {
        int[] image = new int[4];

        Solidify.apply(image, 2, 2);

        assertArrayEquals(new int[] {TRANSPARENT, TRANSPARENT, TRANSPARENT, TRANSPARENT}, image);
    }

    @Test
    void aFullyDrawnImageIsLeftAlone() {
        int[] image = {RED, GREEN, BLUE, RED};

        Solidify.apply(image, 2, 2);

        assertArrayEquals(new int[] {RED, GREEN, BLUE, RED}, image);
    }
}
