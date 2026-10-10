package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eminus.model.port.MipStrategy;
import com.eminus.model.port.Sprite;

import org.junit.jupiter.api.Test;

class FaceMipTest {
    private static final Sprite MEAN = new MipSprite(MipStrategy.MEAN);
    private static final Sprite CUTOUT = new MipSprite(MipStrategy.CUTOUT);

    @Test
    void aFaceNoSpriteDrewIsMean() {
        assertEquals(FaceMip.MEAN, FaceMip.drawnBy(new Sprite[4]));
    }

    @Test
    void theSpriteCoveringMostTexelsDecidesTheStrategy() {
        assertEquals(new FaceMip(MipStrategy.CUTOUT), FaceMip.drawnBy(new Sprite[] {MEAN, CUTOUT, null, CUTOUT}));
    }

    private record MipSprite(MipStrategy mipStrategy) implements Sprite {
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
