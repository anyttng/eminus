package com.eminus.model.port;

public enum VariantDraw {
    NEXT_INT,
    // mc/1.21: GameBlockModel answers it, because WeightedBakedModel draws abs((int) nextLong()) % total.
    NEXT_LONG_MODULO
}
