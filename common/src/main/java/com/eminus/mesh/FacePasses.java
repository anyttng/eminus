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
                FacePlane negative = scratch.negativePlane();
                FacePlane positive = scratch.positivePlane();
                negative.clear();
                positive.clear();

                if (!fill(plane, negative, positive)) {
                    return false;
                }

                if (!negative.isEmpty()) {
                    sink.accept(towardsLow, plane, negative);
                }

                if (!positive.isEmpty()) {
                    sink.accept(towardsHigh, plane, positive);
                }
            }
        }

        return true;
    }

    private boolean fill(int plane, FacePlane negative, FacePlane positive) {
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

                if (!Quad.fitsModelId(modelId)) {
                    scratch.buffer().dropUnaddressable();
                    continue;
                }

                long low = RowMasks.entryAt(voxels, axis, u, v, plane - 1);
                long high = RowMasks.entryAt(voxels, axis, u, v, plane + 1);
                int metadata = models.metadata(modelId);

                if (masks.opaque(row, plane)) {
                    if (masks.facesNegative(row, plane)) {
                        negative.set(u, v, data(owner, low, metadata, modelId));
                    }

                    if (masks.facesPositive(row, plane)) {
                        positive.set(u, v, data(owner, high, metadata, modelId));
                    }

                    continue;
                }

                int lowMetadata = facingMetadata(low);
                int highMetadata = facingMetadata(high);
                if (lowMetadata == MeshModels.MISSING || highMetadata == MeshModels.MISSING) {
                    return false;
                }

                if (visible(metadata, lowMetadata, towardsLow)) {
                    negative.set(u, v, data(owner, low, metadata, modelId));
                }

                if (visible(metadata, highMetadata, towardsHigh)) {
                    positive.set(u, v, data(owner, high, metadata, modelId));
                }
            }
        }

        return true;
    }

    private int facingMetadata(long entry) {
        if (VoxelEntry.isAir(entry)) {
            return NO_METADATA;
        }

        int modelId = models.modelId(VoxelEntry.state(entry), whenBaked);
        return modelId == MeshModels.MISSING ? MeshModels.MISSING : models.metadata(modelId);
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
