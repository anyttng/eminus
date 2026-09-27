package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Random;

import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.util.FastColor.ARGB32;

import org.junit.jupiter.api.Test;

class ArgbTest {
    private static final long SEED = 151L;
    private static final int SAMPLES = 200_000;
    private static final int WHITE = -1;
    private static final int TINT = 0x8033_6699;
    private static final int OPAQUE = 0xFF00_0000;
    private static final int CHANNEL_MAX = 255;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final String GAME_BLEND = "alphaBlend";

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

    @Test
    void meanMatchesTheGamesMipBlend() throws ReflectiveOperationException {
        Method blend = MipmapGenerator.class.getDeclaredMethod(GAME_BLEND,
                int.class, int.class, int.class, int.class, boolean.class);
        blend.setAccessible(true);

        for (int value = 0; value <= CHANNEL_MAX; value++) {
            int grey = OPAQUE | value << RED_SHIFT | value << GREEN_SHIFT | value;
            assertEquals(blend.invoke(null, grey, grey, grey, grey, false),
                    Argb.meanGamma(grey, grey, grey, grey), Integer.toHexString(grey));
        }

        Random random = new Random(SEED);
        for (int sample = 0; sample < SAMPLES; sample++) {
            int first = random.nextInt();
            int second = random.nextInt();
            int third = random.nextInt();
            int fourth = random.nextInt();
            assertEquals(blend.invoke(null, first, second, third, fourth, false),
                    Argb.meanGamma(first, second, third, fourth));
        }
    }
}
