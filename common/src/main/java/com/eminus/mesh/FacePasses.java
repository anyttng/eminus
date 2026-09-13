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
    private final int level;
    private final Runnable whenBaked;
    private final Sink sink;

    private Direction.Axis axis;
    private Direction towardsLow;
    private Direction towardsHigh;

    public FacePasses(MeshScratch scratch, StateOpacity opacity, MeshModels models, int level,
            Runnable whenBaked, Sink sink) {
        this.scratch = scratch;
        this.opacity = opacity;
        this.models = models;
        this.level = level;
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
                boolean lowCovered = covered(u, plane - 1);
                boolean highCovered = covered(u, plane + 1);

                if (!blockFaces(u, v, plane, row, owner, low, high, modelId, lowCovered, highCovered)) {
                    return false;
                }

                if (!fluidFaces(u, v, owner, low, high, lowCovered, highCovered)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean blockFaces(int u, int v, int plane, int row, long owner, long low, long high, int modelId,
            boolean lowCovered, boolean highCovered) {
        if (!Quad.fitsModelId(modelId)) {
            scratch.buffer().dropUnaddressable();
            return true;
        }

        RowMasks masks = scratch.masks();
        int metadata = models.metadata(modelId);

        if (masks.opaque(row, plane)) {
            if (lowCovered && masks.facesNegative(row, plane)) {
                scratch.negativePlane().set(u, v, data(owner, low, metadata, modelId));
            }

            if (highCovered && masks.facesPositive(row, plane)) {
                scratch.positivePlane().set(u, v, data(owner, high, metadata, modelId));
            }

            return true;
        }

        int lowModel = facingModel(low);
        int highModel = facingModel(high);
        if (lowModel == MeshModels.MISSING || highModel == MeshModels.MISSING) {
            return false;
        }

        if (lowCovered && !facingHoldsSameTranslucent(metadata, modelId, lowModel, low)
                && visible(metadata, metadataOf(lowModel), towardsLow)) {
            scratch.negativePlane().set(u, v, data(owner, low, metadata, modelId));
        }

        if (highCovered && !facingHoldsSameTranslucent(metadata, modelId, highModel, high)
                && visible(metadata, metadataOf(highModel), towardsHigh)) {
            scratch.positivePlane().set(u, v, data(owner, high, metadata, modelId));
        }

        return true;
    }

    private boolean covered(int u, int at) {
        CellVoxels voxels = scratch.voxels();

        return switch (axis) {
            case X -> voxels.covered(at, u);
            case Y -> true;
            case Z -> voxels.covered(u, at);
        };
    }

    private boolean fluidFaces(int u, int v, long owner, long low, long high, boolean lowCovered,
            boolean highCovered) {
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

        int metadata = models.metadata(fluidModel);

        if (lowCovered && !facingHoldsSameTranslucent(metadata, fluidModel, lowModel, low)
                && visible(metadata, metadataOf(lowModel), towardsLow)) {
            scratch.negativeFluidPlane().set(u, v, data(owner, low, metadata, fluidModel));
        }

        if (highCovered && !facingHoldsSameTranslucent(metadata, fluidModel, highModel, high)
                && visible(metadata, metadataOf(highModel), towardsHigh)) {
            scratch.positiveFluidPlane().set(u, v, data(owner, high, metadata, fluidModel));
        }

        return true;
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

    private long data(long owner, long facing, int metadata, int modelId) {
        return Quad.data(QuadLight.of(facing, metadata, level), modelId, VoxelEntry.biome(owner));
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
