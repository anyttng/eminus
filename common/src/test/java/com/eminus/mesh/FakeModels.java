package com.eminus.mesh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.eminus.cell.FaceMask;
import com.eminus.model.BiomeColours;
import com.eminus.model.ModelMetadata;

import net.minecraft.world.level.block.state.BlockState;

final class FakeModels implements MeshModels {
    private static final int FIRST_DERIVED_MODEL = 1000;

    @FunctionalInterface
    interface PositionalIds {
        int at(int blockX, int blockY, int blockZ);
    }

    private final Map<Integer, Integer> ids = new HashMap<>();
    private final Map<Integer, Integer> fluidIds = new HashMap<>();
    private final Map<Integer, Integer> submergedIds = new HashMap<>();
    private final Map<Integer, Integer> words = new HashMap<>();
    private final Map<Integer, Integer> tintRows = new HashMap<>();
    private final Map<Integer, BlockState> offsetStates = new HashMap<>();
    private final Map<Integer, PositionalIds> positional = new HashMap<>();
    private final Set<Integer> unbaked = new HashSet<>();
    private final Set<Integer> unbakedFluids = new HashSet<>();
    private final Map<Integer, Integer> fluidKinds = new HashMap<>();
    private final Map<Integer, Float> fluidHeights = new HashMap<>();
    private final Set<Integer> solids = new HashSet<>();
    private final Set<Integer> partialHeights = new HashSet<>();
    private final List<Runnable> waiters = new ArrayList<>();
    private final Map<Integer, Integer> oneSidedIds = new HashMap<>();
    private final Map<List<Integer>, Integer> inwardIds = new HashMap<>();

    private int requests;
    private int nextDerived = FIRST_DERIVED_MODEL;
    private boolean throwOnMetadata;

    void define(int stateId, int modelId, int metadata) {
        ids.put(stateId, modelId);
        words.put(modelId, metadata);
    }

    void positional(int stateId, PositionalIds ids) {
        positional.put(stateId, ids);
    }

    void describe(int modelId, int metadata) {
        words.put(modelId, metadata);
    }

    void tint(int modelId, int row) {
        tintRows.put(modelId, row);
    }

    void offsetLike(int stateId, BlockState state) {
        offsetStates.put(stateId, state);
    }

    void defineFluid(int stateId, int fluidModelId, int metadata) {
        fluidIds.put(stateId, fluidModelId);
        words.put(fluidModelId, metadata);
    }

    void submerge(int surfaceModelId, int submergedModelId) {
        submergedIds.put(surfaceModelId, submergedModelId);
        words.put(submergedModelId, words.get(surfaceModelId));
    }

    void holds(int stateId, int fluid, float height) {
        fluidKinds.put(stateId, fluid);
        fluidHeights.put(stateId, height);
    }

    void makeSolid(int stateId) {
        solids.add(stateId);
    }

    void partialHeight(int modelId) {
        partialHeights.add(modelId);
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

    private int derive(int metadata, int heightFrom) {
        int id = nextDerived++;
        words.put(id, metadata);
        if (partialHeights.contains(heightFrom)) {
            partialHeights.add(id);
        }

        return id;
    }

    int waiting() {
        return waiters.size();
    }

    void publishBakes() {
        List<Runnable> due = List.copyOf(waiters);
        waiters.clear();
        due.forEach(Runnable::run);
    }

    @Override
    public int modelId(int stateId, Runnable whenBaked) {
        if (unbaked.contains(stateId)) {
            requests++;
            waiters.add(whenBaked);
            return MISSING;
        }

        if (positional.containsKey(stateId)) {
            return POSITIONAL;
        }

        Integer modelId = ids.get(stateId);
        return modelId == null ? MISSING : modelId;
    }

    @Override
    public int positionalModelId(int stateId, int blockX, int blockY, int blockZ, Runnable whenBaked) {
        return positional.get(stateId).at(blockX, blockY, blockZ);
    }

    @Override
    public int fluidModelId(int stateId, Runnable whenBaked) {
        if (unbakedFluids.contains(stateId)) {
            requests++;
            waiters.add(whenBaked);
            return MISSING;
        }

        Integer fluidModelId = fluidIds.get(stateId);
        return fluidModelId == null ? NO_FLUID : fluidModelId;
    }

    @Override
    public int submergedModelId(int modelId) {
        return submergedIds.getOrDefault(modelId, modelId);
    }

    @Override
    public int oneSidedModelId(int modelId) {
        return oneSidedIds.computeIfAbsent(modelId,
                id -> derive(words.getOrDefault(id, 0) | ModelMetadata.ONE_SIDED, id));
    }

    @Override
    public int inwardModelId(int seabedModelId, int fluidModelId) {
        return inwardIds.computeIfAbsent(List.of(seabedModelId, fluidModelId), pair -> {
            int id = derive(ModelMetadata.pack(FaceMask.ALL, FaceMask.NONE, FaceMask.NONE, 0,
                    ModelMetadata.FLUID | ModelMetadata.ONE_SIDED | ModelMetadata.INWARD), fluidModelId);
            tintRows.put(id, tintRow(seabedModelId));
            return id;
        });
    }

    @Override
    public int metadata(int modelId) {
        if (throwOnMetadata) {
            throw new IllegalStateException("The fake model table refuses to answer.");
        }

        return words.getOrDefault(modelId, 0);
    }

    @Override
    public int tintRow(int modelId) {
        return tintRows.getOrDefault(modelId, BiomeColours.NO_ROW);
    }

    @Override
    public boolean fillsHeight(int modelId) {
        return !partialHeights.contains(modelId);
    }

    @Override
    public int offset(int stateId, int blockX, int blockY, int blockZ) {
        BlockState state = offsetStates.get(stateId);
        return state == null ? QuadOffset.NONE : QuadOffset.of(state, blockX, blockY, blockZ);
    }

    @Override
    public boolean holdsFluid(int stateId) {
        return fluidKinds.containsKey(stateId);
    }

    @Override
    public boolean sameFluid(int stateId, int otherStateId) {
        Integer fluid = fluidKinds.get(stateId);
        return fluid != null && fluid.equals(fluidKinds.get(otherStateId));
    }

    @Override
    public float fluidHeight(int stateId) {
        return fluidHeights.getOrDefault(stateId, 0.0F);
    }

    @Override
    public boolean solid(int stateId) {
        return solids.contains(stateId);
    }
}
