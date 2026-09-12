package com.eminus.mesh;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class FakeModels implements MeshModels {
    private final Map<Integer, Integer> ids = new HashMap<>();
    private final Map<Integer, Integer> fluidIds = new HashMap<>();
    private final Map<Integer, Integer> words = new HashMap<>();
    private final Set<Integer> unbaked = new HashSet<>();
    private final Set<Integer> unbakedFluids = new HashSet<>();

    private int requests;
    private boolean throwOnMetadata;

    void define(int stateId, int modelId, int metadata) {
        ids.put(stateId, modelId);
        words.put(modelId, metadata);
    }

    void defineFluid(int stateId, int fluidModelId, int metadata) {
        fluidIds.put(stateId, fluidModelId);
        words.put(fluidModelId, metadata);
    }

    void unbake(int stateId) {
        unbaked.add(stateId);
    }

    void unbakeFluid(int stateId) {
        unbakedFluids.add(stateId);
    }

    void throwOnMetadata() {
        throwOnMetadata = true;
    }

    int requests() {
        return requests;
    }

    @Override
    public int modelId(int stateId, Runnable whenBaked) {
        if (unbaked.contains(stateId)) {
            requests++;
            return MISSING;
        }

        Integer modelId = ids.get(stateId);
        return modelId == null ? MISSING : modelId;
    }

    @Override
    public int fluidModelId(int stateId, Runnable whenBaked) {
        if (unbakedFluids.contains(stateId)) {
            requests++;
            return MISSING;
        }

        Integer fluidModelId = fluidIds.get(stateId);
        return fluidModelId == null ? NO_FLUID : fluidModelId;
    }

    @Override
    public int metadata(int modelId) {
        if (throwOnMetadata) {
            throw new IllegalStateException("The fake model table refuses to answer.");
        }

        return words.getOrDefault(modelId, 0);
    }
}
