package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.eminus.model.port.VariantDraw;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import org.jspecify.annotations.Nullable;
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

    private record Variants(List<WeightedEntry.Wrapper<Integer>> entries, int[] table, int total) {
        static Variants of(int... weights) {
            List<WeightedEntry.Wrapper<Integer>> entries = new ArrayList<>();
            int[] table = new int[weights.length * BakedModel.VARIANT_WORDS];
            int upperBound = 0;
            for (int entry = 0; entry < weights.length; entry++) {
                entries.add(WeightedEntry.wrap(FIRST_MODEL + entry, weights[entry]));
                upperBound += weights[entry];
                table[entry * BakedModel.VARIANT_WORDS] = upperBound;
                table[entry * BakedModel.VARIANT_WORDS + 1] = FIRST_MODEL + entry;
            }

            return new Variants(entries, table, upperBound);
        }

        int weightedItem(int index) {
            return WeightedRandom.getWeightedItem(entries, index).orElseThrow().data();
        }

        WeightedBakedModel gameModel() {
            List<WeightedEntry.Wrapper<net.minecraft.client.resources.model.BakedModel>> models = new ArrayList<>();
            for (WeightedEntry.Wrapper<Integer> entry : entries) {
                models.add(WeightedEntry.wrap(new Marker(entry.data()), entry.weight().asInt()));
            }

            return new WeightedBakedModel(models);
        }
    }

    private record Marker(int modelId) implements net.minecraft.client.resources.model.BakedModel {
        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return List.of(new BakedQuad(new int[0], modelId, Direction.UP, null, false));
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean usesBlockLight() {
            return false;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemTransforms getTransforms() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemOverrides getOverrides() {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void equalWeightsOfAPowerOfTwoPickWhatTheGamePicks() {
        assertLongModuloPicksLikeTheGame(1, 1, 1, 1);
    }

    @Test
    void aTotalThatIsNoPowerOfTwoPicksWhatTheGamePicks() {
        assertLongModuloPicksLikeTheGame(1, 2, 4);
    }

    @Test
    void unevenWeightsPickWhatTheGamePicks() {
        assertLongModuloPicksLikeTheGame(3, 50, 1, 17);
    }

    @Test
    void aZeroWeightEntryIsNeverPicked() {
        assertLongModuloPicksLikeTheGame(2, 0, 3);
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

    private static void assertLongModuloPicksLikeTheGame(int... weights) {
        Variants variants = Variants.of(weights);
        WeightedBakedModel game = variants.gameModel();
        Set<Integer> picked = new HashSet<>();

        for (int x : HORIZONTAL) {
            for (int z : HORIZONTAL) {
                for (int y : VERTICAL) {
                    for (int step = 0; step < STRIDES; step++) {
                        int blockX = x + step * STRIDE;
                        int blockZ = z - step * STRIDE;
                        RandomSource random = new LegacyRandomSource(Mth.getSeed(blockX, y, blockZ));
                        int expected = game.getQuads(null, null, random).getFirst().getTintIndex();
                        assertEquals(expected, VariantPick.modelId(variants.table(), blockX, y, blockZ,
                                VariantDraw.NEXT_LONG_MODULO), "block " + blockX + ", " + y + ", " + blockZ);
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
