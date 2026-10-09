package com.eminus.compat.iris.dh;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

final class DhMaterials {
    static final int UNKNOWN = 0;
    static final int LEAVES = 1;
    static final int STONE = 2;
    static final int WOOD = 3;
    static final int METAL = 4;
    static final int DIRT = 5;
    static final int LAVA = 6;
    static final int DEEPSLATE = 7;
    static final int SNOW = 8;
    static final int SAND = 9;
    static final int TERRACOTTA = 10;
    static final int NETHER_STONE = 11;
    static final int WATER = 12;
    static final int GRASS = 13;
    static final int AIR = 14;
    static final int ILLUMINATED = 15;

    static final ToIntFunction<BlockState> CLASSES = DhMaterials::classOf;

    private static final int DARK = 0;
    private static final Set<SoundType> WOOD_SOUNDS = Set.of(SoundType.WOOD, SoundType.NETHER_WOOD,
            SoundType.CHERRY_WOOD, SoundType.BAMBOO_WOOD, SoundType.STEM);
    private static final Map<MapColor, Integer> BY_MAP_COLOUR = byMapColour();
    private static final Map<SoundType, Integer> BY_SOUND = bySound();

    private DhMaterials() {
    }

    static int classOf(BlockState state) {
        if (state.isAir()) {
            return AIR;
        }

        boolean lit = state.getLightEmission() > DARK;
        if (state.getBlock() instanceof LiquidBlock) {
            return lit ? LAVA : WATER;
        }
        if (lit) {
            return ILLUMINATED;
        }
        if (state.getBlock() instanceof LeavesBlock) {
            return LEAVES;
        }

        SoundType sound = state.getSoundType();
        if (WOOD_SOUNDS.contains(sound)) {
            return WOOD;
        }

        Integer byColour = BY_MAP_COLOUR.get(state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        return byColour != null ? byColour : BY_SOUND.getOrDefault(sound, UNKNOWN);
    }

    private static Map<MapColor, Integer> byMapColour() {
        Map<MapColor, Integer> classes = new IdentityHashMap<>();
        put(classes, GRASS, MapColor.GRASS, MapColor.PLANT);
        put(classes, DIRT, MapColor.DIRT);
        put(classes, SAND, MapColor.SAND);
        put(classes, SNOW, MapColor.SNOW);
        put(classes, STONE, MapColor.STONE);
        put(classes, DEEPSLATE, MapColor.DEEPSLATE);
        put(classes, NETHER_STONE, MapColor.NETHER, MapColor.CRIMSON_NYLIUM, MapColor.WARPED_NYLIUM);
        put(classes, WOOD, MapColor.WOOD);
        put(classes, METAL, MapColor.METAL);
        put(classes, TERRACOTTA, MapColor.TERRACOTTA_WHITE, MapColor.TERRACOTTA_ORANGE, MapColor.TERRACOTTA_MAGENTA,
                MapColor.TERRACOTTA_LIGHT_BLUE, MapColor.TERRACOTTA_YELLOW, MapColor.TERRACOTTA_LIGHT_GREEN,
                MapColor.TERRACOTTA_PINK, MapColor.TERRACOTTA_GRAY, MapColor.TERRACOTTA_LIGHT_GRAY,
                MapColor.TERRACOTTA_CYAN, MapColor.TERRACOTTA_PURPLE, MapColor.TERRACOTTA_BLUE,
                MapColor.TERRACOTTA_BROWN, MapColor.TERRACOTTA_GREEN, MapColor.TERRACOTTA_RED,
                MapColor.TERRACOTTA_BLACK);
        return classes;
    }

    private static Map<SoundType, Integer> bySound() {
        Map<SoundType, Integer> classes = new IdentityHashMap<>();
        put(classes, METAL, SoundType.METAL, SoundType.COPPER, SoundType.NETHERITE_BLOCK,
                SoundType.ANVIL, SoundType.CHAIN);
        put(classes, SAND, SoundType.SAND, SoundType.SUSPICIOUS_SAND);
        put(classes, SNOW, SoundType.SNOW, SoundType.POWDER_SNOW);
        put(classes, DIRT, SoundType.GRAVEL, SoundType.ROOTED_DIRT, SoundType.MUD);
        put(classes, DEEPSLATE, SoundType.DEEPSLATE, SoundType.DEEPSLATE_BRICKS, SoundType.DEEPSLATE_TILES,
                SoundType.POLISHED_DEEPSLATE);
        put(classes, NETHER_STONE, SoundType.NETHERRACK, SoundType.NYLIUM, SoundType.NETHER_BRICKS, SoundType.BASALT,
                SoundType.NETHER_ORE, SoundType.NETHER_GOLD_ORE, SoundType.SOUL_SOIL, SoundType.SOUL_SAND);
        put(classes, STONE, SoundType.STONE, SoundType.TUFF, SoundType.CALCITE, SoundType.DRIPSTONE_BLOCK);
        return classes;
    }

    @SafeVarargs
    private static <K> void put(Map<K, Integer> classes, int materialClass, K... keys) {
        for (K key : keys) {
            classes.put(key, materialClass);
        }
    }
}
