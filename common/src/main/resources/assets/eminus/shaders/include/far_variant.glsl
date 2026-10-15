#ifndef EMINUS_FAR_VARIANT_GLSL
#define EMINUS_FAR_VARIANT_GLSL

struct EminusLong {
    uint low;
    uint high;
};

const uint EMINUS_HALF_MASK = 0xFFFFu;
const uint EMINUS_HALF_BITS = 16u;
const uint EMINUS_STATE_HIGH_MASK = 0xFFFFu;
const uint EMINUS_POSITIVE_MASK = 0x7FFFFFFFu;
const uint EMINUS_SEED_X = 3129871u;
const EminusLong EMINUS_SEED_Z = EminusLong(116129781u, 0u);
const EminusLong EMINUS_SEED_SQUARE = EminusLong(42317861u, 0u);
const EminusLong EMINUS_SEED_LINEAR = EminusLong(11u, 0u);
const EminusLong EMINUS_LCG_MULTIPLIER = EminusLong(0xDEECE66Du, 0x5u);
const EminusLong EMINUS_LCG_INCREMENT = EminusLong(11u, 0u);

EminusLong eminus_long(int value) {
    return EminusLong(uint(value), value < 0 ? 0xFFFFFFFFu : 0u);
}

EminusLong eminus_multiply_words(uint a, uint b) {
    uint aLow = a & EMINUS_HALF_MASK;
    uint aHigh = a >> EMINUS_HALF_BITS;
    uint bLow = b & EMINUS_HALF_MASK;
    uint bHigh = b >> EMINUS_HALF_BITS;
    uint lowLow = aLow * bLow;
    uint lowHigh = aLow * bHigh;
    uint highLow = aHigh * bLow;
    uint highHigh = aHigh * bHigh;
    uint middle = (lowLow >> EMINUS_HALF_BITS) + (lowHigh & EMINUS_HALF_MASK) + (highLow & EMINUS_HALF_MASK);
    return EminusLong((lowLow & EMINUS_HALF_MASK) | (middle << EMINUS_HALF_BITS),
                   highHigh + (lowHigh >> EMINUS_HALF_BITS) + (highLow >> EMINUS_HALF_BITS) + (middle >> EMINUS_HALF_BITS));
}

EminusLong eminus_multiply(EminusLong a, EminusLong b) {
    EminusLong product = eminus_multiply_words(a.low, b.low);
    product.high += a.low * b.high + a.high * b.low;
    return product;
}

EminusLong eminus_add(EminusLong a, EminusLong b) {
    uint low = a.low + b.low;
    return EminusLong(low, a.high + b.high + (low < a.low ? 1u : 0u));
}

EminusLong eminus_xor(EminusLong a, EminusLong b) {
    return EminusLong(a.low ^ b.low, a.high ^ b.high);
}

EminusLong eminus_block_random(ivec3 block) {
    EminusLong seed = eminus_xor(eminus_xor(eminus_long(int(uint(block.x) * EMINUS_SEED_X)),
                                   eminus_multiply(eminus_long(block.z), EMINUS_SEED_Z)),
                           eminus_long(block.y));
    EminusLong mixed = eminus_add(eminus_multiply(eminus_multiply(seed, seed), EMINUS_SEED_SQUARE),
                            eminus_multiply(seed, EMINUS_SEED_LINEAR));
    uint low = (mixed.low >> 16u) | (mixed.high << 16u);
    uint high = mixed.high >> 16u;
    return EminusLong(low ^ EMINUS_LCG_MULTIPLIER.low, (high ^ EMINUS_LCG_MULTIPLIER.high) & EMINUS_STATE_HIGH_MASK);
}

void eminus_advance(inout EminusLong state) {
    state = eminus_add(eminus_multiply(state, EMINUS_LCG_MULTIPLIER), EMINUS_LCG_INCREMENT);
    state.high &= EMINUS_STATE_HIGH_MASK;
}

#ifdef VARIANT_NEXT_LONG_MODULO
const uint EMINUS_SIGN_BIT = 0x80000000u;

int eminus_next32(inout EminusLong state) {
    eminus_advance(state);
    return int((state.low >> EMINUS_HALF_BITS) | (state.high << EMINUS_HALF_BITS));
}

int eminus_pick(inout EminusLong state, int bound) {
    eminus_next32(state);
    int low = eminus_next32(state);
    uint magnitude = low < 0 ? 0u - uint(low) : uint(low);
    int modulo = int(magnitude % uint(bound));
    return uint(low) == EMINUS_SIGN_BIT ? -modulo : modulo;
}
#else
int eminus_next31(inout EminusLong state) {
    eminus_advance(state);
    return int(((state.low >> 17u) | (state.high << 15u)) & EMINUS_POSITIVE_MASK);
}

int eminus_pick(inout EminusLong state, int bound) {
    if ((bound & (bound - 1)) == 0) {
        EminusLong product = eminus_multiply_words(uint(bound), uint(eminus_next31(state)));
        return int((product.low >> 31u) | (product.high << 1u));
    }

    int draw = eminus_next31(state);
    int modulo = draw % bound;
    for (int attempt = 0; attempt < MAX_VARIANT_REJECTIONS
            && uint(draw - modulo) + uint(bound - 1) > EMINUS_POSITIVE_MASK; attempt++) {
        draw = eminus_next31(state);
        modulo = draw % bound;
    }

    return modulo;
}
#endif

int eminus_variant_model(int start, int count, ivec3 block) {
    int total = int(texelFetch(EminusModelVariants,start + count - 1).r);
    EminusLong state = eminus_block_random(block);
    int selection = eminus_pick(state, total);

    for (int entry = 0; entry < count; entry++) {
        uvec4 variant = texelFetch(EminusModelVariants,start + entry);
        if (selection < int(variant.r)) {
            return int(variant.g);
        }
    }

    return int(texelFetch(EminusModelVariants,start).g);
}

#endif
