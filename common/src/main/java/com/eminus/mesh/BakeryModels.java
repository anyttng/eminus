package com.eminus.mesh;

import com.eminus.cell.FaceMask;
import com.eminus.model.BakedModel;
import com.eminus.model.BiomeColors;
import com.eminus.model.ModelIndex;
import com.eminus.model.ModelMetadata;
import com.eminus.model.ModelSource;

import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
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
    public int opaqueModelId(int modelId) {
        return index.opaqueModelId(modelId);
    }

    @Override
    public int[] pieceIds(int modelId) {
        return index.pieceIds(modelId);
    }

    @Override
    public int overlayModelId(int modelId) {
        return index.overlayModelId(modelId);
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
    public float inset(int modelId, int face) {
        BakedModel model = models.model(modelId);
        return model == null ? BakedModel.EMPTY_INSET : model.insets()[face];
    }

    @Override
    public int solidFaces(int modelId) {
        BakedModel model = models.model(modelId);
        return model == null || ModelMetadata.has(model.metadata(), ModelMetadata.MOVED) ? FaceMask.NONE
                : model.solidFaces();
    }

    @Override
    public boolean skipsFace(int stateId, int facingStateId, Direction face) {
        return index.state(stateId).skipRendering(index.state(facingStateId), face);
    }

    @Override
    public boolean overlaysFluid(int stateId, int facingStateId) {
        Block facing = index.state(facingStateId).getBlock();
        return index.fluidOverlaid(stateId) && (facing instanceof HalfTransparentBlock || facing instanceof LeavesBlock);
    }

    @Override
    public int tintRow(int modelId) {
        BakedModel model = models.model(modelId);
        return model == null ? BiomeColors.NO_ROW : model.tintRow();
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
        return !index.fluidHolder(stateId).getFluidState().isEmpty();
    }

    @Override
    public boolean sameFluid(int stateId, int otherStateId) {
        FluidState fluid = index.fluidHolder(stateId).getFluidState();
        return !fluid.isEmpty() && fluid.getType().isSame(index.fluidHolder(otherStateId).getFluidState().getType());
    }

    @Override
    public boolean water(int stateId) {
        return index.fluidHolder(stateId).getFluidState().is(FluidTags.WATER);
    }

    @Override
    public float fluidHeight(int stateId) {
        return index.fluidHolder(stateId).getFluidState().getOwnHeight();
    }

    @Override
    public int layerBlocks(int stateId) {
        return index.layerBlocks(stateId);
    }

    @Override
    public boolean solid(int stateId) {
        return index.state(stateId).isSolid();
    }

    @Override
    public int shade(int stateId) {
        return StateShade.of(index.state(stateId));
    }
}
