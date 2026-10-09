package com.eminus.model;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

final class SeedOverrides {
    private static final String SEED_METHOD = "getSeed";
    private static final Class<?>[] SEED_PARAMETERS = {BlockState.class, BlockPos.class};
    private static final Map<Class<?>, Boolean> OVERRIDDEN = new ConcurrentHashMap<>();

    static boolean overridden(Block block) {
        return OVERRIDDEN.computeIfAbsent(block.getClass(), SeedOverrides::declaresBelowBehaviour);
    }

    private static boolean declaresBelowBehaviour(Class<?> type) {
        for (Class<?> at = type; at != BlockBehaviour.class && at != null; at = at.getSuperclass()) {
            for (Method method : at.getDeclaredMethods()) {
                if (method.getName().equals(SEED_METHOD)
                        && Arrays.equals(method.getParameterTypes(), SEED_PARAMETERS)) {
                    return true;
                }
            }
        }

        return false;
    }

    private SeedOverrides() {
    }
}
