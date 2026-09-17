package com.eminus.model;

final class VariantPick {
    private static final int ENTRY_WORDS = BakedModel.VARIANT_WORDS;
    private static final int MAX_REJECTIONS = BakedModel.MAX_VARIANT_REJECTIONS;

    private static final int X_FACTOR = 3129871;
    private static final int Z_FACTOR = 116129781;
    private static final int SQUARE_FACTOR = 42317861;
    private static final int LINEAR_FACTOR = 11;
    private static final int SEED_SHIFT = 16;
    private static final int MULTIPLIER_LOW = 0xDEEC_E66D;
    private static final int MULTIPLIER_HIGH = 0x5;
    private static final int INCREMENT = 11;
    private static final int STATE_HIGH_MASK = 0xFFFF;
    private static final int NEXT_LOW_SHIFT = 17;
    private static final int NEXT_HIGH_SHIFT = 15;
    private static final int POSITIVE_MASK = 0x7FFF_FFFF;
    private static final int POWER_SHIFT = 31;
    private static final int HALF_BITS = 16;
    private static final int HALF_MASK = 0xFFFF;
    private static final int LOW = 0;
    private static final int HIGH = 1;

    static int modelId(int[] table, int blockX, int blockY, int blockZ) {
        int entries = table.length / ENTRY_WORDS;
        int total = table[(entries - 1) * ENTRY_WORDS];
        int selection = selection(blockX, blockY, blockZ, total);

        for (int entry = 0; entry < entries; entry++) {
            if (selection < table[entry * ENTRY_WORDS]) {
                return table[entry * ENTRY_WORDS + 1];
            }
        }

        return table[1];
    }

    static int selection(int blockX, int blockY, int blockZ, int totalWeight) {
        int[] state = seed(blockX, blockY, blockZ);
        if ((totalWeight & (totalWeight - 1)) == 0) {
            int[] product = multiplyWords(totalWeight, next31(state));
            return product[LOW] >>> POWER_SHIFT | product[HIGH] << 1;
        }

        int sample = next31(state);
        int modulo = sample % totalWeight;
        for (int attempt = 0; attempt < MAX_REJECTIONS
                && Integer.compareUnsigned(sample - modulo + (totalWeight - 1), POSITIVE_MASK) > 0; attempt++) {
            sample = next31(state);
            modulo = sample % totalWeight;
        }

        return modulo;
    }

    private static int[] seed(int blockX, int blockY, int blockZ) {
        int[] seed = xor(xor(extend(blockX * X_FACTOR), multiply(extend(blockZ), words(Z_FACTOR, 0))),
                extend(blockY));
        int[] mixed = add(multiply(multiply(seed, seed), words(SQUARE_FACTOR, 0)),
                multiply(seed, words(LINEAR_FACTOR, 0)));
        int low = mixed[LOW] >>> SEED_SHIFT | mixed[HIGH] << SEED_SHIFT;
        int high = mixed[HIGH] >>> SEED_SHIFT;
        return words(low ^ MULTIPLIER_LOW, (high ^ MULTIPLIER_HIGH) & STATE_HIGH_MASK);
    }

    private static int next31(int[] state) {
        int[] next = add(multiply(state, words(MULTIPLIER_LOW, MULTIPLIER_HIGH)), words(INCREMENT, 0));
        state[LOW] = next[LOW];
        state[HIGH] = next[HIGH] & STATE_HIGH_MASK;
        return (state[LOW] >>> NEXT_LOW_SHIFT | state[HIGH] << NEXT_HIGH_SHIFT) & POSITIVE_MASK;
    }

    private static int[] extend(int value) {
        return words(value, value < 0 ? -1 : 0);
    }

    private static int[] xor(int[] a, int[] b) {
        return words(a[LOW] ^ b[LOW], a[HIGH] ^ b[HIGH]);
    }

    private static int[] add(int[] a, int[] b) {
        int low = a[LOW] + b[LOW];
        int carry = Integer.compareUnsigned(low, a[LOW]) < 0 ? 1 : 0;
        return words(low, a[HIGH] + b[HIGH] + carry);
    }

    private static int[] multiply(int[] a, int[] b) {
        int[] product = multiplyWords(a[LOW], b[LOW]);
        product[HIGH] += a[LOW] * b[HIGH] + a[HIGH] * b[LOW];
        return product;
    }

    private static int[] multiplyWords(int a, int b) {
        int aLow = a & HALF_MASK;
        int aHigh = a >>> HALF_BITS;
        int bLow = b & HALF_MASK;
        int bHigh = b >>> HALF_BITS;
        int lowLow = aLow * bLow;
        int lowHigh = aLow * bHigh;
        int highLow = aHigh * bLow;
        int highHigh = aHigh * bHigh;
        int middle = (lowLow >>> HALF_BITS) + (lowHigh & HALF_MASK) + (highLow & HALF_MASK);
        int low = lowLow & HALF_MASK | middle << HALF_BITS;
        int high = highHigh + (lowHigh >>> HALF_BITS) + (highLow >>> HALF_BITS) + (middle >>> HALF_BITS);
        return words(low, high);
    }

    private static int[] words(int low, int high) {
        return new int[] {low, high};
    }

    private VariantPick() {
    }
}
