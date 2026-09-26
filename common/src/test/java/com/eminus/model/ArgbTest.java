package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import net.minecraft.util.FastColor.ARGB32;

import org.junit.jupiter.api.Test;

class ArgbTest {
    private static final long SEED = 151L;
    private static final int SAMPLES = 200_000;
    private static final int WHITE = -1;
    private static final int TINT = 0x8033_6699;

    @Test
    void multiplyAndOpaqueMatchTheGames() {
        Random random = new Random(SEED);
        for (int sample = 0; sample < SAMPLES; sample++) {
            int first = random.nextInt();
            int second = random.nextInt();
            assertEquals(ARGB32.multiply(first, second), Argb.multiply(first, second));
            assertEquals(ARGB32.opaque(first), Argb.opaque(first));
        }

        assertEquals(ARGB32.multiply(WHITE, TINT), Argb.multiply(WHITE, TINT));
        assertEquals(ARGB32.multiply(TINT, WHITE), Argb.multiply(TINT, WHITE));
    }
}
