package com.eminus.model;

import java.util.Arrays;

import com.eminus.cell.StateTable;

public final class ModelIndex {
    private static final int INITIAL_CAPACITY = 256;

    private final StateTable states;
    private final ModelBakery bakery;

    private volatile int[] modelIds = newIds(INITIAL_CAPACITY);

    public ModelIndex(StateTable states, ModelBakery bakery) {
        this.states = states;
        this.bakery = bakery;
    }

    public int modelId(int stateId, Runnable whenBaked) {
        int[] snapshot = modelIds;
        if (stateId >= 0 && stateId < snapshot.length && snapshot[stateId] != ModelBakery.MISSING) {
            return snapshot[stateId];
        }

        int modelId = bakery.request(states.state(stateId), whenBaked);
        if (modelId != ModelBakery.MISSING) {
            remember(stateId, modelId);
        }

        return modelId;
    }

    private synchronized void remember(int stateId, int modelId) {
        int[] current = modelIds;

        if (stateId >= current.length) {
            int size = Math.max(current.length * 2, stateId + 1);
            int[] grown = Arrays.copyOf(current, size);
            Arrays.fill(grown, current.length, size, ModelBakery.MISSING);
            current = grown;
        }

        current[stateId] = modelId;
        modelIds = current;
    }

    private static int[] newIds(int capacity) {
        int[] created = new int[capacity];
        Arrays.fill(created, ModelBakery.MISSING);
        return created;
    }
}
