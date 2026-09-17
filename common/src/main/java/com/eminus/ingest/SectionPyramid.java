package com.eminus.ingest;

import com.eminus.cell.DetailLevel;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

public final class SectionPyramid {
    public static final int SECTION_SIDE = 16;
    public static final int ABSENT = -1;

    private static final int INITIAL_TRANSLATION_CAPACITY = 64;

    private final long[][] levels = new long[DetailLevel.COUNT][];
    private final Reference2IntMap<BlockState> stateIds = newTranslation();
    private final Reference2IntMap<Holder<Biome>> biomeIds = newTranslation();

    public SectionPyramid() {
        for (int level = DetailLevel.MIN; level <= DetailLevel.MAX; level++) {
            int side = sideOf(level);
            levels[level] = new long[side * side * side];
        }
    }

    public static int sideOf(int level) {
        return SECTION_SIDE >> level;
    }

    public static int indexAt(int side, int x, int y, int z) {
        return (y * side + z) * side + x;
    }

    public long[] level(int level) {
        return levels[level];
    }

    public Reference2IntMap<BlockState> stateIds() {
        return stateIds;
    }

    public Reference2IntMap<Holder<Biome>> biomeIds() {
        return biomeIds;
    }

    public void reset() {
        stateIds.clear();
        biomeIds.clear();
    }

    private static <T> Reference2IntMap<T> newTranslation() {
        Reference2IntOpenHashMap<T> translation = new Reference2IntOpenHashMap<>(INITIAL_TRANSLATION_CAPACITY);
        translation.defaultReturnValue(ABSENT);
        return translation;
    }
}
