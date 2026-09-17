#ifndef EMINUS_FAR_VARIANT_GLSL
#define EMINUS_FAR_VARIANT_GLSL

struct FarLong {
    uint low;
    uint high;
};

const uint FAR_HALF_MASK = 0xFFFFu;
const uint FAR_HALF_BITS = 16u;
const uint FAR_STATE_HIGH_MASK = 0xFFFFu;
const uint FAR_POSITIVE_MASK = 0x7FFFFFFFu;
const uint FAR_SEED_X = 3129871u;
const FarLong FAR_SEED_Z = FarLong(116129781u, 0u);
const FarLong FAR_SEED_SQUARE = FarLong(42317861u, 0u);
const FarLong FAR_SEED_LINEAR = FarLong(11u, 0u);
const FarLong FAR_LCG_MULTIPLIER = FarLong(0xDEECE66Du, 0x5u);
const FarLong FAR_LCG_INCREMENT = FarLong(11u, 0u);

FarLong far_long(int value) {
    return FarLong(uint(value), value < 0 ? 0xFFFFFFFFu : 0u);
}

FarLong far_multiply_words(uint a, uint b) {
    uint aLow = a & FAR_HALF_MASK;
    uint aHigh = a >> FAR_HALF_BITS;
    uint bLow = b & FAR_HALF_MASK;
    uint bHigh = b >> FAR_HALF_BITS;
    uint lowLow = aLow * bLow;
    uint lowHigh = aLow * bHigh;
    uint highLow = aHigh * bLow;
    uint highHigh = aHigh * bHigh;
    uint middle = (lowLow >> FAR_HALF_BITS) + (lowHigh & FAR_HALF_MASK) + (highLow & FAR_HALF_MASK);
    return FarLong((lowLow & FAR_HALF_MASK) | (middle << FAR_HALF_BITS),
                   highHigh + (lowHigh >> FAR_HALF_BITS) + (highLow >> FAR_HALF_BITS) + (middle >> FAR_HALF_BITS));
}

FarLong far_multiply(FarLong a, FarLong b) {
    FarLong product = far_multiply_words(a.low, b.low);
    product.high += a.low * b.high + a.high * b.low;
    return product;
}

FarLong far_add(FarLong a, FarLong b) {
    uint low = a.low + b.low;
    return FarLong(low, a.high + b.high + (low < a.low ? 1u : 0u));
}

FarLong far_xor(FarLong a, FarLong b) {
    return FarLong(a.low ^ b.low, a.high ^ b.high);
}

FarLong far_block_random(ivec3 block) {
    FarLong seed = far_xor(far_xor(far_long(int(uint(block.x) * FAR_SEED_X)),
                                   far_multiply(far_long(block.z), FAR_SEED_Z)),
                           far_long(block.y));
    FarLong mixed = far_add(far_multiply(far_multiply(seed, seed), FAR_SEED_SQUARE),
                            far_multiply(seed, FAR_SEED_LINEAR));
    uint low = (mixed.low >> 16u) | (mixed.high << 16u);
    uint high = mixed.high >> 16u;
    return FarLong(low ^ FAR_LCG_MULTIPLIER.low, (high ^ FAR_LCG_MULTIPLIER.high) & FAR_STATE_HIGH_MASK);
}

int far_next31(inout FarLong state) {
    state = far_add(far_multiply(state, FAR_LCG_MULTIPLIER), FAR_LCG_INCREMENT);
    state.high &= FAR_STATE_HIGH_MASK;
    return int(((state.low >> 17u) | (state.high << 15u)) & FAR_POSITIVE_MASK);
}

int far_next_int(inout FarLong state, int bound) {
    if ((bound & (bound - 1)) == 0) {
        FarLong product = far_multiply_words(uint(bound), uint(far_next31(state)));
        return int((product.low >> 31u) | (product.high << 1u));
    }

    int draw = far_next31(state);
    int modulo = draw % bound;
    for (int attempt = 0; attempt < MAX_VARIANT_REJECTIONS
            && uint(draw - modulo) + uint(bound - 1) > FAR_POSITIVE_MASK; attempt++) {
        draw = far_next31(state);
        modulo = draw % bound;
    }

    return modulo;
}

int far_variant_model(int start, int count, ivec3 block) {
    int total = int(texelFetch(ModelVariants, start + count - 1).r);
    FarLong state = far_block_random(block);
    int selection = far_next_int(state, total);

    for (int entry = 0; entry < count; entry++) {
        uvec4 variant = texelFetch(ModelVariants, start + entry);
        if (selection < int(variant.r)) {
            return int(variant.g);
        }
    }

    return int(texelFetch(ModelVariants, start).g);
}

#endif
