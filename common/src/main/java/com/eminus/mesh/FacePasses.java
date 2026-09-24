package com.eminus.mesh;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.FaceMask;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

public final class FacePasses {
    @FunctionalInterface
    public interface Sink {
        void accept(Direction face, int plane, FacePlane quads, boolean border);
    }

    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int FIRST_PLANE = 0;
    private static final int LAST_PLANE = SIDE - 1;
    private static final int LAYER_BELOW = -1;
    private static final int LAYER_ABOVE = SIDE;
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
    private final Runnable whenBaked;
    private final Sink sink;
    private final @Nullable FluidCorners corners;

    private Direction.Axis axis;
    private Direction towardsLow;
    private Direction towardsHigh;

    public FacePasses(MeshScratch scratch, StateOpacity opacity, MeshModels models, int level, Runnable whenBaked,
            Sink sink) {
        this.scratch = scratch;
        this.opacity = opacity;
        this.models = models;
        this.whenBaked = whenBaked;
        this.sink = sink;
        corners = level == FluidCorners.LEVEL ? new FluidCorners(scratch.voxels(), models) : null;
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
        scratch.negativeBorderPlane().clear();
        scratch.positiveBorderPlane().clear();
    }

    private void flush(int plane) {
        send(towardsLow, plane, scratch.negativePlane(), false);
        send(towardsHigh, plane, scratch.positivePlane(), false);
        send(towardsLow, plane, scratch.negativeFluidPlane(), false);
        send(towardsHigh, plane, scratch.positiveFluidPlane(), false);
        send(towardsLow, plane, scratch.negativeBorderPlane(), true);
        send(towardsHigh, plane, scratch.positiveBorderPlane(), true);
    }

    private void send(Direction face, int plane, FacePlane quads, boolean border) {
        if (!quads.isEmpty()) {
            sink.accept(face, plane, quads, border);
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

                if (!blockFaces(u, v, plane, row, owner, low, high, modelId)) {
                    return false;
                }

                if (!fluidFaces(u, v, plane, owner, low, high)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean blockFaces(int u, int v, int plane, int row, long owner, long low, long high, int modelId) {
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
        boolean fluid = ModelMetadata.has(metadata, ModelMetadata.FLUID);
        int stateId = VoxelEntry.state(owner);

        if (masks.opaque(row, plane)) {
            boolean lowFace = masks.facesNegative(row, plane);
            boolean highFace = masks.facesPositive(row, plane);
            boolean lowBorder = !lowFace && plane == FIRST_PLANE && bordered(metadata, towardsLow)
                    && !(fluid && sharesFluid(stateId, low));
            boolean highBorder = !highFace && plane == LAST_PLANE && bordered(metadata, towardsHigh)
                    && !(fluid && sharesFluid(stateId, high));
            if (fluid) {
                placeFluid(scratch.negativePlane(), scratch.positivePlane(), lowFace, highFace, owner,
                        drawn == modelId, AIR_MODEL, metadata, drawn, u, v, plane, low, high);
                placeFluidBorder(lowBorder, highBorder, owner, drawn == modelId, metadata, drawn, u, v, plane);
                return true;
            }

            if (lowFace) {
                scratch.negativePlane().set(u, v, data(u, v, plane, low, metadata, drawn, owner));
            }

            if (highFace) {
                scratch.positivePlane().set(u, v, data(u, v, plane, high, metadata, drawn, owner));
            }

            placeBorder(lowBorder, highBorder, u, v, plane, metadata, drawn, owner);
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

        boolean lowCovered = covered(owner, low, towardsLow);
        boolean highCovered = covered(owner, high, towardsHigh);
        boolean lowKept = !(lowCovered && facingHoldsSameTranslucent(metadata, modelId, lowModel, low, u, v, plane - 1))
                && !(lowCovered && facingDrawsSameFluid(modelId, drawn, lowDrawn))
                && !(fluid && slopesInto(stateId, low));
        boolean highKept = !(highCovered
                        && facingHoldsSameTranslucent(metadata, modelId, highModel, high, u, v, plane + 1))
                && !(highCovered && facingDrawsSameFluid(modelId, drawn, highDrawn))
                && !(fluid && slopesInto(stateId, high));
        boolean lowFace = lowKept && visible(metadata, metadataOf(lowDrawn), towardsLow, lowCovered);
        boolean highFace = highKept && visible(metadata, metadataOf(highDrawn), towardsHigh, highCovered);
        boolean lowBorder = lowKept && !lowFace && plane == FIRST_PLANE && bordered(metadata, towardsLow);
        boolean highBorder = highKept && !highFace && plane == LAST_PLANE && bordered(metadata, towardsHigh);

        if (fluid) {
            placeFluid(scratch.negativePlane(), scratch.positivePlane(), lowFace, highFace, owner,
                    drawn == modelId, highDrawn, metadata, drawn, u, v, plane, low, high);
            placeFluidBorder(lowBorder, highBorder, owner, drawn == modelId, metadata, drawn, u, v, plane);
            return true;
        }

        if (lowFace) {
            scratch.negativePlane().set(u, v, data(u, v, plane, low, metadata, drawn, owner));
        }

        if (highFace) {
            scratch.positivePlane().set(u, v, data(u, v, plane, high, metadata, drawn, owner));
        }

        placeBorder(lowBorder, highBorder, u, v, plane, metadata, drawn, owner);
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
        int stateId = VoxelEntry.state(owner);

        boolean lowCovered = covered(owner, low, towardsLow);
        boolean highCovered = covered(owner, high, towardsHigh);
        boolean lowKept = !(lowCovered
                        && facingHoldsSameTranslucent(metadata, fluidModel, lowModel, low, u, v, plane - 1))
                && !(lowCovered && facingDrawsSameFluid(fluidModel, drawn, lowDrawn))
                && !slopesInto(stateId, low);
        boolean highKept = !(highCovered
                        && facingHoldsSameTranslucent(metadata, fluidModel, highModel, high, u, v, plane + 1))
                && !(highCovered && facingDrawsSameFluid(fluidModel, drawn, highDrawn))
                && !slopesInto(stateId, high);
        boolean lowFace = lowKept && visible(metadata, metadataOf(lowDrawn), towardsLow, lowCovered);
        boolean highFace = highKept && visible(metadata, metadataOf(highDrawn), towardsHigh, highCovered);
        placeFluid(scratch.negativeFluidPlane(), scratch.positiveFluidPlane(), lowFace, highFace, owner,
                drawn == fluidModel, highDrawn, metadata, drawn, u, v, plane, low, high);
        placeFluidBorder(lowKept && !lowFace && plane == FIRST_PLANE && bordered(metadata, towardsLow),
                highKept && !highFace && plane == LAST_PLANE && bordered(metadata, towardsHigh), owner,
                drawn == fluidModel, metadata, drawn, u, v, plane);
        return true;
    }

    private void placeBorder(boolean lowBorder, boolean highBorder, int u, int v, int plane, int metadata,
            int drawn, long owner) {
        if (lowBorder) {
            scratch.negativeBorderPlane().set(u, v, data(u, v, plane, borderLit(u, v, plane - 1), metadata, drawn,
                    owner));
        }

        if (highBorder) {
            scratch.positiveBorderPlane().set(u, v, data(u, v, plane, borderLit(u, v, plane + 1), metadata, drawn,
                    owner));
        }
    }

    private void placeFluidBorder(boolean lowBorder, boolean highBorder, long owner, boolean surface, int metadata,
            int drawn, int u, int v, int plane) {
        if (lowBorder || highBorder) {
            placeFluid(scratch.negativeBorderPlane(), scratch.positiveBorderPlane(), lowBorder, highBorder, owner,
                    surface, AIR_MODEL, metadata, drawn, u, v, plane, borderLit(u, v, plane - 1),
                    borderLit(u, v, plane + 1));
        }
    }

    private long borderLit(int u, int v, int facingPlane) {
        CellVoxels voxels = scratch.voxels();
        if (axis == Direction.Axis.Y) {
            for (int up = facingPlane + 1; up < SIDE; up++) {
                long entry = RowMasks.entryAt(voxels, axis, u, v, up);
                if (open(entry)) {
                    return entry;
                }
            }
        } else {
            for (int up = v + 1; up < SIDE; up++) {
                long entry = RowMasks.entryAt(voxels, axis, u, up, facingPlane);
                if (open(entry)) {
                    return entry;
                }
            }
        }

        return VoxelEntry.AIR;
    }

    private static boolean open(long entry) {
        return VoxelEntry.isAir(entry) || VoxelEntry.gaps(entry) != VoxelEntry.NO_GAPS;
    }

    private static boolean bordered(int metadata, Direction face) {
        return (ModelMetadata.present(metadata) & FaceMask.bit(face)) != 0
                && !ModelMetadata.has(metadata, ModelMetadata.TRANSLUCENT);
    }

    private void placeFluid(FacePlane negative, FacePlane positive, boolean lowFace, boolean highFace, long owner,
            boolean surface, int highDrawn, int metadata, int drawn, int u, int v, int plane, long low, long high) {
        if (!lowFace && !highFace) {
            return;
        }

        FluidCorners sloping = corners;
        int shape = sloping != null && surface
                ? cornersAt(sloping, VoxelEntry.state(owner), u, v, plane)
                : FluidCorners.FLAT;
        boolean topShown = highFace && !(axis == Direction.Axis.Y && FluidCorners.full(shape)
                && (ModelMetadata.occluding(metadataOf(highDrawn)) & FaceMask.DOWN) != 0);

        if (lowFace) {
            negative.set(u, v, fluidData(u, v, plane, low, metadata, drawn, shape, owner));
        }

        if (topShown) {
            positive.set(u, v, fluidData(u, v, plane, high, metadata, drawn, shape, owner));
        }
    }

    private boolean slopesInto(int stateId, long facing) {
        return corners != null && sharesFluid(stateId, facing);
    }

    private boolean sharesFluid(int stateId, long facing) {
        return !VoxelEntry.isAir(facing) && models.sameFluid(stateId, VoxelEntry.state(facing));
    }

    private int cornersAt(FluidCorners sloping, int stateId, int u, int v, int plane) {
        return switch (axis) {
            case X -> sloping.of(stateId, plane, v, u);
            case Y -> sloping.of(stateId, u, plane, v);
            case Z -> sloping.of(stateId, u, v, plane);
        };
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

    private long data(int u, int v, int plane, long facing, int metadata, int modelId, long owner) {
        int colourIndex = switch (axis) {
            case X -> QuadTint.of(scratch, models, owner, modelId, plane, v, u);
            case Y -> QuadTint.of(scratch, models, owner, modelId, u, plane, v);
            case Z -> QuadTint.of(scratch, models, owner, modelId, u, v, plane);
        };

        return Quad.data(QuadLight.of(facing, metadata), modelId, colourIndex);
    }

    private long fluidData(int u, int v, int plane, long facing, int metadata, int modelId, int surface,
            long owner) {
        int colourIndex = switch (axis) {
            case X -> QuadTint.ofFluid(scratch, models, owner, modelId, surface, plane, v, u);
            case Y -> QuadTint.ofFluid(scratch, models, owner, modelId, surface, u, plane, v);
            case Z -> QuadTint.ofFluid(scratch, models, owner, modelId, surface, u, v, plane);
        };

        return Quad.data(QuadLight.of(facing, metadata), modelId, colourIndex);
    }

    private static boolean held(int v, int plane) {
        boolean vInside = v >= 0 && v < SIDE;
        boolean planeInside = plane >= 0 && plane < SIDE;
        return v >= LAYER_BELOW && v <= LAYER_ABOVE && plane >= LAYER_BELOW && plane <= LAYER_ABOVE
                && (vInside || planeInside);
    }

    private static boolean covered(long owner, long facing, Direction face) {
        return switch (face) {
            case UP -> VoxelEntry.highGap(owner) == 0 && VoxelEntry.lowGap(facing) == 0;
            case DOWN -> VoxelEntry.lowGap(owner) == 0 && VoxelEntry.highGap(facing) == 0;
            default -> VoxelEntry.lowGap(facing) <= VoxelEntry.lowGap(owner)
                    && VoxelEntry.highGap(facing) <= VoxelEntry.highGap(owner);
        };
    }

    private static boolean visible(int metadata, int facingMetadata, Direction face, boolean covered) {
        int bit = FaceMask.bit(face);
        if ((ModelMetadata.present(metadata) & bit) == 0) {
            return false;
        }

        if ((ModelMetadata.occludable(metadata) & bit) == 0 || !covered) {
            return true;
        }

        return (ModelMetadata.occluding(facingMetadata) & FaceMask.bit(face.getOpposite())) == 0;
    }
}
