package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.eminus.model.port.VariantDraw;

import net.minecraft.util.Mth;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

import org.junit.jupiter.api.Test;

class VariantPickTest {
    private static final int FIRST_MODEL = 100;
    private static final int[] HORIZONTAL = {-29_999_999, -1_048_577, -4097, -33, -1, 0, 1, 15, 16, 511, 70_001,
            2_000_003, 29_999_998};
    private static final int[] VERTICAL = {-64, -1, 0, 63, 64, 200, 319};
    private static final int STRIDE = 37;
    private static final int STRIDES = 12;
    private static final int[] LOW_DRAWS = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -7, -1, 0, 5,
            Integer.MAX_VALUE};
    private static final int[] BOUNDS = {1, 4, 5, 7, 71};

    private interface Pick {
        int at(int blockX, int blockY, int blockZ);
    }

    private record Variants(List<Weighted<Integer>> entries, int[] table, int total) {
        static Variants of(int... weights) {
            List<Weighted<Integer>> entries = new ArrayList<>();
            int[] table = new int[weights.length * BakedModel.VARIANT_WORDS];
            int upperBound = 0;
            for (int entry = 0; entry < weights.length; entry++) {
                entries.add(new Weighted<>(FIRST_MODEL + entry, weights[entry]));
                upperBound += weights[entry];
                table[entry * BakedModel.VARIANT_WORDS] = upperBound;
                table[entry * BakedModel.VARIANT_WORDS + 1] = FIRST_MODEL + entry;
            }

            return new Variants(entries, table, upperBound);
        }

        int weightedItem(int index) {
            return WeightedRandom.getWeightedItem(entries, index, Weighted::weight).orElseThrow().value();
        }
    }

    @Test
    void equalWeightsOfAPowerOfTwoPickWhatTheGamePicks() {
        assertNextIntPicksLikeTheGame(1, 1, 1, 1);
    }

    @Test
    void aTotalThatIsNoPowerOfTwoPicksWhatTheGamePicks() {
        assertNextIntPicksLikeTheGame(1, 2, 4);
    }

    @Test
    void unevenWeightsPastTheFlatSelectorPickWhatTheGamePicks() {
        assertNextIntPicksLikeTheGame(3, 50, 1, 17);
    }

    @Test
    void aZeroWeightEntryIsNeverPicked() {
        assertNextIntPicksLikeTheGame(2, 0, 3);
    }

    @Test
    void equalWeightsOfAPowerOfTwoDrawnByLongModuloPickWhatTheReferencePicks() {
        assertLongModuloPicksLikeTheReference(1, 1, 1, 1);
    }

    @Test
    void aTotalThatIsNoPowerOfTwoDrawnByLongModuloPicksWhatTheReferencePicks() {
        assertLongModuloPicksLikeTheReference(1, 2, 4);
    }

    @Test
    void unevenWeightsDrawnByLongModuloPickWhatTheReferencePicks() {
        assertLongModuloPicksLikeTheReference(3, 50, 1, 17);
    }

    @Test
    void aZeroWeightEntryIsNeverPickedByTheLongModuloDraw() {
        assertLongModuloPicksLikeTheReference(2, 0, 3);
    }

    @Test
    void theAbsoluteModuloOfEveryLowDrawIsWhatJavaComputes() {
        for (int low : LOW_DRAWS) {
            for (int bound : BOUNDS) {
                assertEquals(Math.abs(low) % bound, VariantPick.absModulo(low, bound), low + " % " + bound);
            }
        }
    }

    @Test
    void theLowestDrawSelectsTheEntryTheGameSelects() {
        for (int[] weights : new int[][] {{2, 0, 3}, {0, 1, 1, 2}, {1, 2, 4}}) {
            Variants variants = Variants.of(weights);
            int selection = Math.abs(Integer.MIN_VALUE) % variants.total();
            assertEquals(variants.weightedItem(selection), VariantPick.lookup(variants.table(), selection),
                    Arrays.toString(weights));
        }
    }

    private static void assertNextIntPicksLikeTheGame(int... weights) {
        Variants variants = Variants.of(weights);
        WeightedList<Integer> list = WeightedList.of(variants.entries());
        assertMirrorPicks(variants, VariantDraw.NEXT_INT, weights, (blockX, blockY, blockZ) ->
                list.getRandomOrThrow(new SingleThreadedRandomSource(Mth.getSeed(blockX, blockY, blockZ))));
    }

    private static void assertLongModuloPicksLikeTheReference(int... weights) {
        Variants variants = Variants.of(weights);
        assertMirrorPicks(variants, VariantDraw.NEXT_LONG_MODULO, weights, (blockX, blockY, blockZ) -> {
            LegacyRandomSource random = new LegacyRandomSource(Mth.getSeed(blockX, blockY, blockZ));
            return variants.weightedItem(Math.abs((int) random.nextLong()) % variants.total());
        });
    }

    private static void assertMirrorPicks(Variants variants, VariantDraw draw, int[] weights, Pick reference) {
        Set<Integer> picked = new HashSet<>();

        for (int x : HORIZONTAL) {
            for (int z : HORIZONTAL) {
                for (int y : VERTICAL) {
                    for (int step = 0; step < STRIDES; step++) {
                        int blockX = x + step * STRIDE;
                        int blockZ = z - step * STRIDE;
                        int expected = reference.at(blockX, y, blockZ);
                        assertEquals(expected, VariantPick.modelId(variants.table(), blockX, y, blockZ, draw),
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
