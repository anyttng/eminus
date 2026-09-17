package com.eminus.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

final class SeedOverrides {
    private static final String SEED_METHOD = "getSeed";
    private static final Map<Class<?>, Boolean> OVERRIDDEN = new ConcurrentHashMap<>();

    static boolean overridden(Block block) {
        return OVERRIDDEN.computeIfAbsent(block.getClass(), SeedOverrides::declaresBelowBehaviour);
    }

    private static boolean declaresBelowBehaviour(Class<?> type) {
        for (Class<?> at = type; at != BlockBehaviour.class && at != null; at = at.getSuperclass()) {
            try {
                at.getDeclaredMethod(SEED_METHOD, BlockState.class, BlockPos.class);
                return true;
            } catch (NoSuchMethodException absent) {
                continue;
            }
        }

        return false;
    }

    private SeedOverrides() {
    }
}
