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
                int modelId = models.modelId(VoxelEntry.state(owner), whenBaked);
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
        int metadata = models.metadata(modelId);

        int drawn = drawnModel(modelId, u, v, plane);
        if (drawn == MeshModels.MISSING) {
            return false;
        }

        if (!Quad.fitsModelId(drawn)) {
            scratch.buffer().dropUnaddressable();
            return true;
        }

        if (masks.opaque(row, plane)) {
            if (masks.facesNegative(row, plane)) {
                scratch.negativePlane().set(u, v, data(u, v, plane, low, metadata, drawn, stateId));
            }

            if (masks.facesPositive(row, plane)) {
                scratch.positivePlane().set(u, v, data(u, v, plane, high, metadata, drawn, stateId));
            }

            return true;
        }

        int lowModel = facingModel(low);
        int highModel = facingModel(high);
        if (lowModel == MeshModels.MISSING || highModel == MeshModels.MISSING) {
            return false;
        }

        if (!facingHoldsSameTranslucent(metadata, modelId, lowModel, low)
                && visible(metadata, metadataOf(lowModel), towardsLow)) {
            scratch.negativePlane().set(u, v, data(u, v, plane, low, metadata, drawn, stateId));
        }

        if (!facingHoldsSameTranslucent(metadata, modelId, highModel, high)
                && visible(metadata, metadataOf(highModel), towardsHigh)) {
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

        int lowModel = facingFluidModel(low);
        int highModel = facingFluidModel(high);
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

        int metadata = models.metadata(fluidModel);

        if (!facingHoldsSameTranslucent(metadata, fluidModel, lowModel, low)
                && visible(metadata, metadataOf(lowModel), towardsLow)) {
            scratch.negativeFluidPlane().set(u, v, data(u, v, plane, low, metadata, drawn, NO_OFFSET_STATE));
        }

        if (!facingHoldsSameTranslucent(metadata, fluidModel, highModel, high)
                && visible(metadata, metadataOf(highModel), towardsHigh)) {
            scratch.positiveFluidPlane().set(u, v, data(u, v, plane, high, metadata, drawn, NO_OFFSET_STATE));
        }

        return true;
    }

    private int drawnModel(int surfaceModel, int u, int v, int plane) {
        int submergedModel = models.submergedModelId(surfaceModel);
        if (submergedModel == surfaceModel) {
            return surfaceModel;
        }

        long above = axis == Direction.Axis.Y
                ? RowMasks.entryAt(scratch.voxels(), axis, u, v, plane + 1)
                : RowMasks.entryAt(scratch.voxels(), axis, u, v + 1, plane);
        int aboveModel = facingFluidModel(above);
        if (aboveModel == MeshModels.MISSING) {
            return MeshModels.MISSING;
        }

        return aboveModel == surfaceModel ? submergedModel : surfaceModel;
    }

    private int facingModel(long entry) {
        return VoxelEntry.isAir(entry) ? AIR_MODEL : models.modelId(VoxelEntry.state(entry), whenBaked);
    }

    private int facingFluidModel(long entry) {
        if (VoxelEntry.isAir(entry)) {
            return AIR_MODEL;
        }

        int stateId = VoxelEntry.state(entry);
        int fluidModel = models.fluidModelId(stateId, whenBaked);
        return fluidModel == MeshModels.NO_FLUID ? models.modelId(stateId, whenBaked) : fluidModel;
    }

    private int metadataOf(int modelId) {
        return modelId == AIR_MODEL ? NO_METADATA : models.metadata(modelId);
    }

    private boolean facingHoldsSameTranslucent(int metadata, int modelId, int facingModel, long facing) {
        if (!ModelMetadata.has(metadata, ModelMetadata.TRANSLUCENT)) {
            return false;
        }

        return modelId == facingModel || modelId == facingFluidModel(facing);
    }

    private long data(int u, int v, int plane, long facing, int metadata, int modelId, int stateId) {
        int colourIndex = switch (axis) {
            case X -> QuadTint.of(scratch, models, stateId, modelId, plane, v, u);
            case Y -> QuadTint.of(scratch, models, stateId, modelId, u, plane, v);
            case Z -> QuadTint.of(scratch, models, stateId, modelId, u, v, plane);
        };

        return Quad.data(QuadLight.of(facing, metadata), modelId, colourIndex);
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
