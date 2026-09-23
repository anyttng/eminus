package com.eminus.mesh;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.FaceMask;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

public final class FacePasses {
    @FunctionalInterface
    public interface Sink {
        void accept(Direction face, int plane, FacePlane quads);
    }

    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int LAYER_BELOW = -1;
    private static final int LAYER_ABOVE = SIDE;
    private static final int NO_METADATA = 0;
    private static final int AIR_MODEL = -3;
    private static final int NO_OFFSET_STATE = VoxelEntry.AIR_STATE_ID;
    private static final Direction[] TOWARDS_LOW = new Direction[Direction.Axis.values().length];
    private static final Direction[] TOWARDS_HIGH = new Direction[Direction.Axis.values().length];

    static {
        for (Direction face : Direction.values()) {
            Direction[] side = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? TOWARDS_HIGH
                    : TOWARDS_LOW;
            side[face.getAxis().ordinal()] = face;
        }
    }

    private final MeshScratch scratch;
    private final StateOpacity opacity;
    private final MeshModels models;
    private final Runnable whenBaked;
    private final Sink sink;

    private Direction.Axis axis;
    private Direction towardsLow;
    private Direction towardsHigh;

    public FacePasses(MeshScratch scratch, StateOpacity opacity, MeshModels models, Runnable whenBaked, Sink sink) {
        this.scratch = scratch;
        this.opacity = opacity;
        this.models = models;
        this.whenBaked = whenBaked;
        this.sink = sink;
    }

    public boolean run() {
        for (Direction.Axis along : Direction.Axis.values()) {
            axis = along;
            towardsLow = TOWARDS_LOW[along.ordinal()];
            towardsHigh = TOWARDS_HIGH[along.ordinal()];
            scratch.masks().build(scratch.voxels(), opacity, along);

            for (int plane = 0; plane < SIDE; plane++) {
                clearPlanes();

                if (!fill(plane)) {
                    return false;
                }

                flush(plane);
            }
        }

        return true;
    }

    private void clearPlanes() {
        scratch.negativePlane().clear();
        scratch.positivePlane().clear();
        scratch.negativeFluidPlane().clear();
        scratch.positiveFluidPlane().clear();
    }

    private void flush(int plane) {
        send(towardsLow, plane, scratch.negativePlane());
        send(towardsHigh, plane, scratch.positivePlane());
        send(towardsLow, plane, scratch.negativeFluidPlane());
        send(towardsHigh, plane, scratch.positiveFluidPlane());
    }

    private void send(Direction face, int plane, FacePlane quads) {
        if (!quads.isEmpty()) {
            sink.accept(face, plane, quads);
        }
    }

    private boolean fill(int plane) {
        CellVoxels voxels = scratch.voxels();
        RowMasks masks = scratch.masks();

        for (int v = 0; v < SIDE; v++) {
            for (int u = 0; u < SIDE; u++) {
                int row = v * SIDE + u;
                if (!masks.solid(row, plane)) {
                    continue;
                }

                long owner = RowMasks.entryAt(voxels, axis, u, v, plane);
                int modelId = modelAt(VoxelEntry.state(owner), u, v, plane);
                if (modelId == MeshModels.MISSING) {
                    return false;
                }

                long low = RowMasks.entryAt(voxels, axis, u, v, plane - 1);
                long high = RowMasks.entryAt(voxels, axis, u, v, plane + 1);

                if (!blockFaces(u, v, plane, row, low, high, VoxelEntry.state(owner), modelId)) {
                    return false;
                }

                if (!fluidFaces(u, v, plane, owner, low, high)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean blockFaces(int u, int v, int plane, int row, long low, long high, int stateId, int modelId) {
        if (!Quad.fitsModelId(modelId)) {
            scratch.buffer().dropUnaddressable();
            return true;
        }

        RowMasks masks = scratch.masks();

        int drawn = drawnModel(modelId, u, v, plane);
        if (drawn == MeshModels.MISSING) {
            return false;
        }

        if (!Quad.fitsModelId(drawn)) {
            scratch.buffer().dropUnaddressable();
            return true;
        }

        int metadata = models.metadata(drawn);

        if (masks.opaque(row, plane)) {
            if (masks.facesNegative(row, plane)) {
                scratch.negativePlane().set(u, v, data(u, v, plane, low, metadata, drawn, stateId));
            }

            if (masks.facesPositive(row, plane)) {
                scratch.positivePlane().set(u, v, data(u, v, plane, high, metadata, drawn, stateId));
            }

            return true;
        }

        int lowModel = facingModel(low, u, v, plane - 1);
        int highModel = facingModel(high, u, v, plane + 1);
        if (lowModel == MeshModels.MISSING || highModel == MeshModels.MISSING) {
            return false;
        }

        int lowDrawn = drawnModel(lowModel, u, v, plane - 1);
        int highDrawn = drawnModel(highModel, u, v, plane + 1);
        if (lowDrawn == MeshModels.MISSING || highDrawn == MeshModels.MISSING) {
            return false;
        }

        if (!facingHoldsSameTranslucent(metadata, modelId, lowModel, low, u, v, plane - 1)
                && !facingDrawsSameFluid(modelId, drawn, lowDrawn)
                && visible(metadata, metadataOf(lowDrawn), towardsLow)) {
            scratch.negativePlane().set(u, v, data(u, v, plane, low, metadata, drawn, stateId));
        }

        if (!facingHoldsSameTranslucent(metadata, modelId, highModel, high, u, v, plane + 1)
                && !facingDrawsSameFluid(modelId, drawn, highDrawn)
                && visible(metadata, metadataOf(highDrawn), towardsHigh)) {
            scratch.positivePlane().set(u, v, data(u, v, plane, high, metadata, drawn, stateId));
        }

        return true;
    }

    private boolean fluidFaces(int u, int v, int plane, long owner, long low, long high) {
        int fluidModel = models.fluidModelId(VoxelEntry.state(owner), whenBaked);
        if (fluidModel == MeshModels.MISSING) {
            return false;
        }

        if (fluidModel == MeshModels.NO_FLUID) {
            return true;
        }

        if (!Quad.fitsModelId(fluidModel)) {
            scratch.buffer().dropUnaddressable();
            return true;
        }

        int lowModel = facingFluidModel(low, u, v, plane - 1);
        int highModel = facingFluidModel(high, u, v, plane + 1);
        if (lowModel == MeshModels.MISSING || highModel == MeshModels.MISSING) {
            return false;
        }

        int drawn = drawnModel(fluidModel, u, v, plane);
        if (drawn == MeshModels.MISSING) {
            return false;
        }

        if (!Quad.fitsModelId(drawn)) {
            scratch.buffer().dropUnaddressable();
            return true;
        }

        int lowDrawn = drawnModel(lowModel, u, v, plane - 1);
        int highDrawn = drawnModel(highModel, u, v, plane + 1);
        if (lowDrawn == MeshModels.MISSING || highDrawn == MeshModels.MISSING) {
            return false;
        }

        int metadata = models.metadata(drawn);

        if (!facingHoldsSameTranslucent(metadata, fluidModel, lowModel, low, u, v, plane - 1)
                && !facingDrawsSameFluid(fluidModel, drawn, lowDrawn)
                && visible(metadata, metadataOf(lowDrawn), towardsLow)) {
            scratch.negativeFluidPlane().set(u, v, data(u, v, plane, low, metadata, drawn, NO_OFFSET_STATE));
        }

        if (!facingHoldsSameTranslucent(metadata, fluidModel, highModel, high, u, v, plane + 1)
                && !facingDrawsSameFluid(fluidModel, drawn, highDrawn)
                && visible(metadata, metadataOf(highDrawn), towardsHigh)) {
            scratch.positiveFluidPlane().set(u, v, data(u, v, plane, high, metadata, drawn, NO_OFFSET_STATE));
        }

        return true;
    }

    private int drawnModel(int surfaceModel, int u, int v, int plane) {
        int submergedModel = models.submergedModelId(surfaceModel);
        if (submergedModel == surfaceModel) {
            return surfaceModel;
        }

        int aboveV = axis == Direction.Axis.Y ? v : v + 1;
        int abovePlane = axis == Direction.Axis.Y ? plane + 1 : plane;
        if (!held(aboveV, abovePlane)) {
            return surfaceModel;
        }

        long above = RowMasks.entryAt(scratch.voxels(), axis, u, aboveV, abovePlane);
        int aboveModel = facingFluidModel(above, u, aboveV, abovePlane);
        if (aboveModel == MeshModels.MISSING) {
            return MeshModels.MISSING;
        }

        return models.submergedModelId(aboveModel) == submergedModel ? submergedModel : surfaceModel;
    }

    private int modelAt(int stateId, int u, int v, int plane) {
        return switch (axis) {
            case X -> scratch.voxelModels().at(models, stateId, plane, v, u, whenBaked);
            case Y -> scratch.voxelModels().at(models, stateId, u, plane, v, whenBaked);
            case Z -> scratch.voxelModels().at(models, stateId, u, v, plane, whenBaked);
        };
    }

    private int facingModel(long entry, int u, int v, int plane) {
        return VoxelEntry.isAir(entry) ? AIR_MODEL : modelAt(VoxelEntry.state(entry), u, v, plane);
    }

    private int facingFluidModel(long entry, int u, int v, int plane) {
        if (VoxelEntry.isAir(entry)) {
            return AIR_MODEL;
        }

        int stateId = VoxelEntry.state(entry);
        int fluidModel = models.fluidModelId(stateId, whenBaked);
        return fluidModel == MeshModels.NO_FLUID ? modelAt(stateId, u, v, plane) : fluidModel;
    }

    private int metadataOf(int modelId) {
        return modelId == AIR_MODEL ? NO_METADATA : models.metadata(modelId);
    }

    private boolean facingHoldsSameTranslucent(int metadata, int modelId, int facingModel, long facing, int u, int v,
            int plane) {
        if (!ModelMetadata.has(metadata, ModelMetadata.TRANSLUCENT)) {
            return false;
        }

        int fluid = models.submergedModelId(modelId);
        return fluid == models.submergedModelId(facingModel)
                || fluid == models.submergedModelId(facingFluidModel(facing, u, v, plane));
    }

    private boolean facingDrawsSameFluid(int modelId, int drawn, int facingDrawn) {
        return drawn == facingDrawn && models.submergedModelId(modelId) != modelId;
    }

    private long data(int u, int v, int plane, long facing, int metadata, int modelId, int stateId) {
        int colourIndex = switch (axis) {
            case X -> QuadTint.of(scratch, models, stateId, modelId, plane, v, u);
            case Y -> QuadTint.of(scratch, models, stateId, modelId, u, plane, v);
            case Z -> QuadTint.of(scratch, models, stateId, modelId, u, v, plane);
        };

        return Quad.data(QuadLight.of(facing, metadata), modelId, colourIndex);
    }

    private static boolean held(int v, int plane) {
        boolean vInside = v >= 0 && v < SIDE;
        boolean planeInside = plane >= 0 && plane < SIDE;
        return v >= LAYER_BELOW && v <= LAYER_ABOVE && plane >= LAYER_BELOW && plane <= LAYER_ABOVE
                && (vInside || planeInside);
    }

    private static boolean visible(int metadata, int facingMetadata, Direction face) {
        int bit = FaceMask.bit(face);
        if ((ModelMetadata.present(metadata) & bit) == 0) {
            return false;
        }

        if ((ModelMetadata.occludable(metadata) & bit) == 0) {
            return true;
        }

        return (ModelMetadata.occluding(facingMetadata) & FaceMask.bit(face.getOpposite())) == 0;
    }
}
