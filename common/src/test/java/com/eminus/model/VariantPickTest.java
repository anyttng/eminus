package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.util.Mth;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

import org.junit.jupiter.api.Test;

class VariantPickTest {
    private static final int FIRST_MODEL = 100;
    private static final int[] HORIZONTAL = {-29_999_999, -1_048_577, -4097, -33, -1, 0, 1, 15, 16, 511, 70_001,
            2_000_003, 29_999_998};
    private static final int[] VERTICAL = {-64, -1, 0, 63, 64, 200, 319};
    private static final int STRIDE = 37;
    private static final int STRIDES = 12;

    @Test
    void equalWeightsOfAPowerOfTwoPickWhatTheGamePicks() {
        assertMirrorPicksLikeTheGame(1, 1, 1, 1);
    }

    @Test
    void aTotalThatIsNoPowerOfTwoPicksWhatTheGamePicks() {
        assertMirrorPicksLikeTheGame(1, 2, 4);
    }

    @Test
    void unevenWeightsPastTheFlatSelectorPickWhatTheGamePicks() {
        assertMirrorPicksLikeTheGame(3, 50, 1, 17);
    }

    @Test
    void aZeroWeightEntryIsNeverPicked() {
        assertMirrorPicksLikeTheGame(2, 0, 3);
    }

    private static void assertMirrorPicksLikeTheGame(int... weights) {
        List<Weighted<Integer>> entries = new ArrayList<>();
        int[] table = new int[weights.length * BakedModel.VARIANT_WORDS];
        int upperBound = 0;
        for (int entry = 0; entry < weights.length; entry++) {
            entries.add(new Weighted<>(FIRST_MODEL + entry, weights[entry]));
            upperBound += weights[entry];
            table[entry * BakedModel.VARIANT_WORDS] = upperBound;
            table[entry * BakedModel.VARIANT_WORDS + 1] = FIRST_MODEL + entry;
        }

        WeightedList<Integer> list = WeightedList.of(entries);
        Set<Integer> picked = new HashSet<>();

        for (int x : HORIZONTAL) {
            for (int z : HORIZONTAL) {
                for (int y : VERTICAL) {
                    for (int step = 0; step < STRIDES; step++) {
                        int blockX = x + step * STRIDE;
                        int blockZ = z - step * STRIDE;
                        int expected = list.getRandomOrThrow(
                                new SingleThreadedRandomSource(Mth.getSeed(blockX, y, blockZ)));
                        assertEquals(expected, VariantPick.modelId(table, blockX, y, blockZ),
                                "block " + blockX + ", " + y + ", " + blockZ);
                        picked.add(expected);
                    }
                }
            }
        }

        long reachable = Arrays.stream(weights).filter(weight -> weight > 0).count();
        assertEquals(reachable, picked.size());
        assertTrue(picked.size() > 1);
    }
}
