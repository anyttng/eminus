package com.eminus.mesh;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.FaceMask;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.StateTable;
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
    private static final int NO_METADATA = 0;

    private static final class Side {
        private final int edge;

        @SuppressWarnings("NullAway.Init")
        private Direction towards;
        private long facing;
        private int x;
        private int y;
        private int z;
        private int model;
        private int drawn;
        private boolean face;
        private boolean border;

        private Side(int edge) {
            this.edge = edge;
        }

        private void at(CellVoxels voxels, int atX, int atY, int atZ) {
            x = atX;
            y = atY;
            z = atZ;
            facing = voxels.entry(atX, atY, atZ);
        }
    }

    private final MeshScratch scratch;
    private final StateOpacity opacity;
    private final MeshModels models;
    private final DrawnModels drawnModels;
    private final Runnable whenBaked;
    private final Sink sink;
    private final @Nullable FluidCorners corners;
    private final FaceOcclusion occlusion;
    private final int voxelBlocks;
    private final Side low = new Side(FIRST_PLANE);
    private final Side high = new Side(LAST_PLANE);

    @SuppressWarnings("NullAway.Init")
    private Direction.Axis axis;
    private int plane;
    private int u;
    private int v;
    private int x;
    private int y;
    private int z;
    private long owner;
    private int stateId;
    private int sourceModel;
    private int drawn;
    private int metadata;
    private boolean liquid;
    @SuppressWarnings("NullAway.Init")
    private FacePlane negativeFaces;
    @SuppressWarnings("NullAway.Init")
    private FacePlane positiveFaces;
    private boolean shaped;
    private int shape;

    public FacePasses(MeshScratch scratch, StateOpacity opacity, MeshModels models, int level, boolean occluded,
            Runnable whenBaked, Sink sink) {
        this.scratch = scratch;
        this.opacity = opacity;
        this.models = models;
        drawnModels = new DrawnModels(scratch, models, whenBaked);
        this.whenBaked = whenBaked;
        this.sink = sink;
        corners = level == FluidCorners.LEVEL ? new FluidCorners(scratch.voxels(), models) : null;
        occlusion = new FaceOcclusion(scratch.voxels(), models, level, occluded);
        voxelBlocks = DetailLevel.blocksPerVoxel(level);
    }

    public boolean run() {
        for (Direction.Axis along : Direction.Axis.values()) {
            axis = along;
            low.towards = Direction.get(Direction.AxisDirection.NEGATIVE, along);
            high.towards = Direction.get(Direction.AxisDirection.POSITIVE, along);
            scratch.masks().build(scratch.voxels(), opacity, voxelBlocks, along);

            for (plane = 0; plane < SIDE; plane++) {
                clearPlanes();

                if (!fill()) {
                    return false;
                }

                flush();
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
        scratch.negativeInwardPlane().clear();
        scratch.positiveInwardPlane().clear();
    }

    private void flush() {
        send(low.towards, scratch.negativePlane(), false);
        send(high.towards, scratch.positivePlane(), false);
        send(low.towards, scratch.negativeFluidPlane(), false);
        send(high.towards, scratch.positiveFluidPlane(), false);
        send(low.towards, scratch.negativeBorderPlane(), true);
        send(high.towards, scratch.positiveBorderPlane(), true);
        send(low.towards, scratch.negativeInwardPlane(), false);
        send(high.towards, scratch.positiveInwardPlane(), false);
    }

    private void send(Direction face, FacePlane quads, boolean border) {
        if (!quads.isEmpty()) {
            sink.accept(face, plane, quads, border);
        }
    }

    private boolean fill() {
        RowMasks masks = scratch.masks();

        for (int atV = 0; atV < SIDE; atV++) {
            for (int atU = 0; atU < SIDE; atU++) {
                int row = atV * SIDE + atU;
                if (!masks.solid(row, plane)) {
                    continue;
                }

                enter(atU, atV);
                int modelId = drawnModels.at(stateId, x, y, z);
                if (modelId == MeshModels.MISSING) {
                    return false;
                }

                if (!blockFaces(row, modelId) || !fluidFaces()) {
                    return false;
                }
            }
        }

        return true;
    }

    private void enter(int atU, int atV) {
        CellVoxels voxels = scratch.voxels();
        u = atU;
        v = atV;
        x = PlaneAxes.x(axis, atU, atV, plane);
        y = PlaneAxes.y(axis, atU, atV, plane);
        z = PlaneAxes.z(axis, atU, atV, plane);
        owner = voxels.entry(x, y, z);
        stateId = VoxelEntry.state(owner);

        Direction step = high.towards;
        low.at(voxels, x - step.getStepX(), y - step.getStepY(), z - step.getStepZ());
        high.at(voxels, x + step.getStepX(), y + step.getStepY(), z + step.getStepZ());
    }

    private boolean blockFaces(int row, int modelId) {
        if (!Quad.fitsModelId(modelId)) {
            scratch.buffer().dropUnaddressable();
            return true;
        }

        int drawnId = drawnModels.drawn(modelId, x, y, z);
        if (drawnId == MeshModels.MISSING) {
            return false;
        }

        if (!Quad.fitsModelId(drawnId)) {
            scratch.buffer().dropUnaddressable();
            return true;
        }

        source(modelId, drawnId, false, scratch.negativePlane(), scratch.positivePlane());

        RowMasks masks = scratch.masks();
        if (masks.opaque(row, plane)) {
            low.face = masks.facesNegative(row, plane);
            high.face = masks.facesPositive(row, plane);
            low.drawn = MeshModels.AIR;
            high.drawn = MeshModels.AIR;
            opaqueBorder(low);
            opaqueBorder(high);
            place(false);
            return true;
        }

        low.model = drawnModels.facing(low.facing, low.x, low.y, low.z);
        high.model = drawnModels.facing(high.facing, high.x, high.y, high.z);
        return sides();
    }

    private boolean fluidFaces() {
        int fluidModel = models.fluidModelId(stateId, whenBaked);
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

        low.model = drawnModels.facingFluid(low.facing, low.x, low.y, low.z);
        high.model = drawnModels.facingFluid(high.facing, high.x, high.y, high.z);
        if (low.model == MeshModels.MISSING || high.model == MeshModels.MISSING) {
            return false;
        }

        int drawnId = drawnModels.drawn(fluidModel, x, y, z);
        if (drawnId == MeshModels.MISSING) {
            return false;
        }

        if (!Quad.fitsModelId(drawnId)) {
            scratch.buffer().dropUnaddressable();
            return true;
        }

        source(fluidModel, drawnId, true, scratch.negativeFluidPlane(), scratch.positiveFluidPlane());
        return sides();
    }

    private void source(int modelId, int drawnId, boolean fluid, FacePlane negative, FacePlane positive) {
        sourceModel = modelId;
        drawn = drawnId;
        metadata = models.metadata(drawnId);
        liquid = fluid || ModelMetadata.has(metadata, ModelMetadata.FLUID);
        negativeFaces = negative;
        positiveFaces = positive;
        shaped = false;
    }

    private boolean sides() {
        if (low.model == MeshModels.MISSING || high.model == MeshModels.MISSING) {
            return false;
        }

        low.drawn = drawnModels.drawn(low.model, low.x, low.y, low.z);
        high.drawn = drawnModels.drawn(high.model, high.x, high.y, high.z);
        if (low.drawn == MeshModels.MISSING || high.drawn == MeshModels.MISSING) {
            return false;
        }

        decide(low);
        decide(high);
        place(true);
        return !liquid || placeInward();
    }

    private void decide(Side side) {
        boolean covered = covered(owner, side.facing, side.towards);
        boolean kept = !(covered && facingHoldsSameTranslucent(side))
                && !(covered && skippedByGame(side))
                && !(covered && facingDrawsSameFluid(side.drawn))
                && !(liquid && slopesInto(side.facing));
        side.face = kept && visible(metadata, occludingFaces(side.facing, side.drawn), side.towards, covered)
                && !shielded(side.facing, side.towards);
        side.border = kept && !side.face && plane == side.edge && bordered(metadata, side.towards);
    }

    private void opaqueBorder(Side side) {
        side.border = !side.face && plane == side.edge && bordered(metadata, side.towards)
                && !(liquid && sharesFluid(side.facing));
    }

    private void place(boolean sidedBlock) {
        placeFace(low, negativeFaces, sidedBlock);
        placeFace(high, positiveFaces, sidedBlock);
        placeBorder(low, scratch.negativeBorderPlane());
        placeBorder(high, scratch.positiveBorderPlane());
    }

    private void placeFace(Side side, FacePlane quads, boolean sidedBlock) {
        if (!side.face) {
            return;
        }

        if (liquid) {
            if (shown(side, occludingFaces(side.facing, side.drawn))) {
                quads.set(u, v, fluidData(side.facing, metadata, sided(drawn, side), shape()), OcclusionWord.NONE);
            }

            return;
        }

        quads.set(u, v, data(side.facing, metadata, sidedBlock ? sided(drawn, side) : drawn), occluded(side));
    }

    private void placeBorder(Side side, FacePlane quads) {
        if (!side.border) {
            return;
        }

        long lit = borderLit(side);
        if (liquid) {
            if (shown(side, FaceMask.NONE)) {
                quads.set(u, v, fluidData(lit, metadata, sided(drawn, side), shape()), OcclusionWord.NONE);
            }

            return;
        }

        quads.set(u, v, data(lit, metadata, drawn), occluded(side));
    }

    private int occluded(Side side) {
        return occlusion.face(side.towards, x, y, z, stateId, metadata,
                models.inset(drawn, side.towards.get3DDataValue()));
    }

    private boolean shown(Side side, int facingOccluding) {
        return side.towards != Direction.UP || !(FluidCorners.full(shape()) && (facingOccluding & FaceMask.DOWN) != 0);
    }

    private int occludingFaces(long facing, int facingDrawn) {
        return opacity.opacity(VoxelEntry.state(facing)) >= StateTable.FULL_OPACITY
                ? FaceMask.ALL
                : ModelMetadata.occluding(metadataOf(facingDrawn));
    }

    private boolean placeInward() {
        boolean lowCut = low.face && cut(low);
        boolean highCut = high.face && cut(high);
        if ((!lowCut && !highCut) || !ModelMetadata.has(metadata, ModelMetadata.TRANSLUCENT)) {
            return true;
        }

        int seabed = drawnModels.seabed(x, y, z);
        if (seabed == MeshModels.MISSING) {
            return false;
        }

        if (seabed == DrawnModels.NO_SEABED) {
            return true;
        }

        int inward = models.inwardModelId(seabed, drawn);
        long quad = fluidData(drawnModels.seabedLight(), models.metadata(inward), inward, shape());
        if (lowCut) {
            scratch.positiveInwardPlane().set(u, v, quad, OcclusionWord.NONE);
        }

        if (highCut) {
            scratch.negativeInwardPlane().set(u, v, quad, OcclusionWord.NONE);
        }

        return true;
    }

    private long borderLit(Side side) {
        CellVoxels voxels = scratch.voxels();
        for (int up = side.y + 1; up < SIDE; up++) {
            long entry = voxels.entry(side.x, up, side.z);
            if (open(entry)) {
                return entry;
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

    private int shape() {
        if (!shaped) {
            FluidCorners sloping = corners;
            shape = sloping != null && drawn == sourceModel ? sloping.of(stateId, x, y, z) : FluidCorners.FLAT;
            shaped = true;
        }

        return shape;
    }

    private int sided(int modelId, Side side) {
        return ModelMetadata.has(metadata, ModelMetadata.TRANSLUCENT) && cut(side)
                ? models.oneSidedModelId(modelId)
                : modelId;
    }

    private boolean cut(Side side) {
        return axis != Direction.Axis.Y && !scratch.voxels().covered(side.x, side.z);
    }

    private boolean slopesInto(long facing) {
        return corners != null && sharesFluid(facing);
    }

    private boolean sharesFluid(long facing) {
        return !VoxelEntry.isAir(facing) && models.sameFluid(stateId, VoxelEntry.state(facing));
    }

    private int metadataOf(int modelId) {
        return modelId == MeshModels.AIR ? NO_METADATA : models.metadata(modelId);
    }

    private boolean facingHoldsSameTranslucent(Side side) {
        if (!ModelMetadata.has(metadata, ModelMetadata.TRANSLUCENT)) {
            return false;
        }

        int fluid = models.submergedModelId(sourceModel);
        return fluid == models.submergedModelId(side.model)
                || fluid == models.submergedModelId(drawnModels.facingFluid(side.facing, side.x, side.y, side.z));
    }

    private boolean skippedByGame(Side side) {
        return !liquid && ModelMetadata.has(metadata, ModelMetadata.TRANSLUCENT) && !VoxelEntry.isAir(side.facing)
                && models.skipsFace(stateId, VoxelEntry.state(side.facing), side.towards);
    }

    private boolean facingDrawsSameFluid(int facingDrawn) {
        return drawn == facingDrawn && models.submergedModelId(sourceModel) != sourceModel;
    }

    private long data(long facing, int quadMetadata, int modelId) {
        int colorIndex = QuadTint.of(scratch, models, owner, modelId, x, y, z);
        return Quad.data(QuadLight.of(facing, quadMetadata), modelId, colorIndex);
    }

    private long fluidData(long facing, int quadMetadata, int modelId, int surface) {
        int colorIndex = QuadTint.ofFluid(scratch, models, owner, modelId, surface, x, y, z);
        return Quad.data(QuadLight.of(facing, quadMetadata), modelId, colorIndex);
    }

    private static boolean covered(long owner, long facing, Direction face) {
        return switch (face) {
            case UP -> VoxelEntry.highGap(owner) == 0 && VoxelEntry.lowGap(facing) == 0;
            case DOWN -> VoxelEntry.lowGap(owner) == 0 && VoxelEntry.highGap(facing) == 0;
            default -> VoxelEntry.lowGap(facing) <= VoxelEntry.lowGap(owner)
                    && VoxelEntry.highGap(facing) <= VoxelEntry.highGap(owner);
        };
    }

    private boolean shielded(long facing, Direction face) {
        return face.getAxis() != Direction.Axis.Y
                && (ModelMetadata.occludable(metadata) & FaceMask.bit(face)) != 0
                && opacity.coversGround(facing, voxelBlocks)
                && VoxelEntry.lowGap(facing) <= VoxelEntry.lowGap(owner)
                && (VoxelEntry.highGap(facing) < VoxelEntry.highGap(owner)
                        || (VoxelEntry.state(facing) == VoxelEntry.state(owner)
                                && VoxelEntry.highGap(facing) == VoxelEntry.highGap(owner)));
    }

    private static boolean visible(int metadata, int facingOccluding, Direction face, boolean covered) {
        int bit = FaceMask.bit(face);
        if ((ModelMetadata.present(metadata) & bit) == 0) {
            return false;
        }

        if ((ModelMetadata.occludable(metadata) & bit) == 0 || !covered) {
            return true;
        }

        return (facingOccluding & FaceMask.bit(face.getOpposite())) == 0;
    }
}
