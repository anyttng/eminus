package com.eminus.model;

import com.eminus.cell.IdTable;
import com.eminus.cell.StateTable;

import net.minecraft.world.level.block.state.BlockState;

public final class ModelIndex {
    private static final int INITIAL_CAPACITY = 256;

    private final StateTable states;
    private final ModelBakery bakery;
    private final IdTable modelIds = new IdTable(INITIAL_CAPACITY, ModelBakery.MISSING);
    private final IdTable fluidIds = new IdTable(INITIAL_CAPACITY, ModelBakery.MISSING);

    public ModelIndex(StateTable states, ModelBakery bakery) {
        this.states = states;
        this.bakery = bakery;
    }

    public int modelId(int stateId, Runnable whenBaked) {
        int known = modelIds.get(stateId);
        if (known != ModelBakery.MISSING) {
            return known;
        }

        return resolve(stateId, whenBaked) ? modelIds.get(stateId) : ModelBakery.MISSING;
    }

    public int fluidModelId(int stateId, Runnable whenBaked) {
        int known = fluidIds.get(stateId);
        if (known != ModelBakery.MISSING) {
            return known;
        }

        return resolve(stateId, whenBaked) ? fluidIds.get(stateId) : ModelBakery.MISSING;
    }

    public int positionalModelId(int stateId, int blockX, int blockY, int blockZ, Runnable whenBaked) {
        return bakery.positionalModelId(states.state(stateId), blockX, blockY, blockZ, whenBaked);
    }

    public int submergedModelId(int modelId) {
        return bakery.submergedModelId(modelId);
    }

    public int oneSidedModelId(int modelId) {
        return bakery.oneSidedModelId(modelId);
    }

    public int inwardModelId(int seabedModelId, int fluidModelId) {
        return bakery.inwardModelId(seabedModelId, fluidModelId);
    }

    public BlockState state(int stateId) {
        return states.state(stateId);
    }

    private boolean resolve(int stateId, Runnable whenBaked) {
        BlockState state = states.state(stateId);
        int modelId = bakery.request(state, whenBaked);
        if (modelId == ModelBakery.MISSING) {
            return false;
        }

        fluidIds.put(stateId, bakery.fluidModelId(state));
        modelIds.put(stateId, bakery.positional(state) ? ModelBakery.POSITIONAL : modelId);
        return true;
    }
}
