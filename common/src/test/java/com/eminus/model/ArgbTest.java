package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Random;

import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.util.ARGB;

import org.junit.jupiter.api.Test;

class ArgbTest {
    private static final long SEED = 151L;
    private static final int SAMPLES = 200_000;
    private static final int CHANNEL_VALUES = 256;
    private static final int WHITE = -1;
    private static final int TINT = 0x8033_6699;
    private static final int CORNERS = 4;
    private static final int RGB_MASK = 0x00FF_FFFF;
    private static final String DARKENED_BLEND = "darkenedAlphaBlend";

    @Test
    void theLinearMeanMatchesTheGamesForEveryGreyAndRandomQuadruples() {
        for (int value = 0; value < CHANNEL_VALUES; value++) {
            int grey = ARGB.color(value, value, value, value);
            int black = ARGB.color(CHANNEL_VALUES - 1 - value, 0, 0, 0);
            assertEquals(ARGB.meanLinear(grey, black, grey, black), Argb.meanLinear(grey, black, grey, black),
                    "value " + value);
        }

        Random random = new Random(SEED);
        for (int sample = 0; sample < SAMPLES; sample++) {
            int first = random.nextInt();
            int second = random.nextInt();
            int third = random.nextInt();
            int fourth = random.nextInt();
            assertEquals(ARGB.meanLinear(first, second, third, fourth), Argb.meanLinear(first, second, third, fourth));
        }
    }

    @Test
    void theDarkenedBlendMatchesTheGamesForRandomQuadruplesWithEmptyTexels() throws ReflectiveOperationException {
        Method game = MipmapGenerator.class.getDeclaredMethod(DARKENED_BLEND, int.class, int.class, int.class,
                int.class);
        game.setAccessible(true);
        Random random = new Random(SEED);

        for (int sample = 0; sample < SAMPLES; sample++) {
            int[] colours = new int[CORNERS];
            for (int corner = 0; corner < CORNERS; corner++) {
                colours[corner] = random.nextBoolean() ? random.nextInt() & RGB_MASK : random.nextInt();
            }

            assertEquals(game.invoke(null, colours[0], colours[1], colours[2], colours[3]),
                    Argb.darkenedAlphaBlend(colours[0], colours[1], colours[2], colours[3]));
        }
    }

    @Test
    void multiplyAndOpaqueMatchTheGames() {
        Random random = new Random(SEED);
        for (int sample = 0; sample < SAMPLES; sample++) {
            int first = random.nextInt();
            int second = random.nextInt();
            assertEquals(ARGB.multiply(first, second), Argb.multiply(first, second));
            assertEquals(ARGB.opaque(first), Argb.opaque(first));
        }

        assertEquals(ARGB.multiply(WHITE, TINT), Argb.multiply(WHITE, TINT));
        assertEquals(ARGB.multiply(TINT, WHITE), Argb.multiply(TINT, WHITE));
    }
}
