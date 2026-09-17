package com.eminus.model;

import java.util.Arrays;

import com.eminus.cell.StateTable;

import net.minecraft.world.level.block.state.BlockState;

public final class ModelIndex {
    private static final int INITIAL_CAPACITY = 256;

    private final StateTable states;
    private final ModelBakery bakery;

    private volatile int[] modelIds = newIds(INITIAL_CAPACITY);
    private volatile int[] fluidIds = newIds(INITIAL_CAPACITY);

    public ModelIndex(StateTable states, ModelBakery bakery) {
        this.states = states;
        this.bakery = bakery;
    }

    public int modelId(int stateId, Runnable whenBaked) {
        int known = remembered(modelIds, stateId);
        if (known != ModelBakery.MISSING) {
            return known;
        }

        return resolve(stateId, whenBaked) ? remembered(modelIds, stateId) : ModelBakery.MISSING;
    }

    public int fluidModelId(int stateId, Runnable whenBaked) {
        int known = remembered(fluidIds, stateId);
        if (known != ModelBakery.MISSING) {
            return known;
        }

        return resolve(stateId, whenBaked) ? remembered(fluidIds, stateId) : ModelBakery.MISSING;
    }

    public int submergedModelId(int modelId) {
        return bakery.submergedModelId(modelId);
    }

    private boolean resolve(int stateId, Runnable whenBaked) {
        BlockState state = states.state(stateId);
        int modelId = bakery.request(state, whenBaked);
        if (modelId == ModelBakery.MISSING) {
            return false;
        }

        remember(stateId, modelId, bakery.fluidModelId(state));
        return true;
    }

    private synchronized void remember(int stateId, int modelId, int fluidId) {
        int[] currentModels = modelIds;
        int[] currentFluids = fluidIds;

        if (stateId >= currentModels.length) {
            int size = Math.max(currentModels.length * 2, stateId + 1);
            currentModels = grown(currentModels, size);
            currentFluids = grown(currentFluids, size);
        }

        currentModels[stateId] = modelId;
        currentFluids[stateId] = fluidId;
        fluidIds = currentFluids;
        modelIds = currentModels;
    }

    private static int remembered(int[] snapshot, int stateId) {
        return stateId >= 0 && stateId < snapshot.length ? snapshot[stateId] : ModelBakery.MISSING;
    }

    private static int[] grown(int[] current, int size) {
        int[] grown = Arrays.copyOf(current, size);
        Arrays.fill(grown, current.length, size, ModelBakery.MISSING);
        return grown;
    }

    private static int[] newIds(int capacity) {
        int[] created = new int[capacity];
        Arrays.fill(created, ModelBakery.MISSING);
        return created;
    }
}
