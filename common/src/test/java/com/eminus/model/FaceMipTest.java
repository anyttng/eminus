package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eminus.model.port.MipStrategy;
import com.eminus.model.port.Sprite;

import org.junit.jupiter.api.Test;

class FaceMipTest {
    private static final float BIAS = 0.1F;
    private static final Sprite STRICT = new MipSprite(MipStrategy.STRICT_CUTOUT, BIAS);
    private static final Sprite CUTOUT = new MipSprite(MipStrategy.CUTOUT, 0.0F);
    private static final Sprite DARK = new MipSprite(MipStrategy.DARK_CUTOUT, 0.0F);

    @Test
    void aFaceNoSpriteDrewIsMean() {
        assertEquals(FaceMip.MEAN, FaceMip.drawnBy(new Sprite[4]));
    }

    @Test
    void anyDarkCutoutTexelMakesTheFaceDarkCutout() {
        assertEquals(FaceMip.of(DARK), FaceMip.drawnBy(new Sprite[] {CUTOUT, CUTOUT, CUTOUT, DARK}));
    }

    @Test
    void otherwiseTheSpriteCoveringMostTexelsDecidesStrategyAndBias() {
        assertEquals(new FaceMip(MipStrategy.STRICT_CUTOUT, BIAS),
                FaceMip.drawnBy(new Sprite[] {CUTOUT, STRICT, null, STRICT}));
    }

    private record MipSprite(MipStrategy mipStrategy, float alphaCutoffBias) implements Sprite {
        @Override
        public int width() {
            return 1;
        }

        @Override
        public int height() {
            return 1;
        }

        @Override
        public float u0() {
            return 0.0F;
        }

        @Override
        public float u1() {
            return 1.0F;
        }

        @Override
        public float v0() {
            return 0.0F;
        }

        @Override
        public float v1() {
            return 1.0F;
        }

        @Override
        public int[] argb() {
            return new int[1];
        }
    }
}
