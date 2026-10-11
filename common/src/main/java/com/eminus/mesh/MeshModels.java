package com.eminus.mesh;

import com.eminus.model.ModelBakery;

import net.minecraft.core.Direction;

public interface MeshModels {
    int MISSING = ModelBakery.MISSING;
    int NO_FLUID = ModelBakery.NO_FLUID;
    int AIR = -3;
    int POSITIONAL = ModelBakery.POSITIONAL;

    int modelId(int stateId, Runnable whenBaked);

    int positionalModelId(int stateId, int blockX, int blockY, int blockZ, Runnable whenBaked);

    int fluidModelId(int stateId, Runnable whenBaked);

    int submergedModelId(int modelId);

    int oneSidedModelId(int modelId);

    int inwardModelId(int seabedModelId, int fluidModelId);

    int metadata(int modelId);

    float inset(int modelId, int face);

    boolean skipsFace(int stateId, int facingStateId, Direction face);

    int tintRow(int modelId);

    boolean fillsHeight(int modelId);

    int offset(int stateId, int blockX, int blockY, int blockZ);

    boolean holdsFluid(int stateId);

    boolean sameFluid(int stateId, int otherStateId);

    float fluidHeight(int stateId);

    boolean solid(int stateId);
}
