package com.eminus.mesh;

import com.eminus.model.BakedModel;
import com.eminus.model.BiomeColours;
import com.eminus.model.ModelIndex;
import com.eminus.model.ModelSource;

import net.minecraft.world.level.material.FluidState;

public record BakeryModels(ModelIndex index, ModelSource models) implements MeshModels {
    private static final int NO_METADATA = 0;

    @Override
    public int modelId(int stateId, Runnable whenBaked) {
        return index.modelId(stateId, whenBaked);
    }

    @Override
    public int positionalModelId(int stateId, int blockX, int blockY, int blockZ, Runnable whenBaked) {
        return index.positionalModelId(stateId, blockX, blockY, blockZ, whenBaked);
    }

    @Override
    public int fluidModelId(int stateId, Runnable whenBaked) {
        return index.fluidModelId(stateId, whenBaked);
    }

    @Override
    public int submergedModelId(int modelId) {
        return index.submergedModelId(modelId);
    }

    @Override
    public int oneSidedModelId(int modelId) {
        return index.oneSidedModelId(modelId);
    }

    @Override
    public int inwardModelId(int seabedModelId, int fluidModelId) {
        return index.inwardModelId(seabedModelId, fluidModelId);
    }

    @Override
    public int metadata(int modelId) {
        BakedModel model = models.model(modelId);
        return model == null ? NO_METADATA : model.metadata();
    }

    @Override
    public int tintRow(int modelId) {
        BakedModel model = models.model(modelId);
        return model == null ? BiomeColours.NO_ROW : model.tintRow();
    }

    @Override
    public boolean fillsHeight(int modelId) {
        BakedModel model = models.model(modelId);
        return model == null || model.fillsHeight();
    }

    @Override
    public int offset(int stateId, int blockX, int blockY, int blockZ) {
        return QuadOffset.of(index.state(stateId), blockX, blockY, blockZ);
    }

    @Override
    public boolean holdsFluid(int stateId) {
        return !index.state(stateId).getFluidState().isEmpty();
    }

    @Override
    public boolean sameFluid(int stateId, int otherStateId) {
        FluidState fluid = index.state(stateId).getFluidState();
        return !fluid.isEmpty() && fluid.getType().isSame(index.state(otherStateId).getFluidState().getType());
    }

    @Override
    public float fluidHeight(int stateId) {
        return index.state(stateId).getFluidState().getOwnHeight();
    }

    @Override
    public boolean solid(int stateId) {
        return index.state(stateId).isSolid();
    }
}
