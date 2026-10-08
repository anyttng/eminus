package com.eminus.mesh;

import com.eminus.cell.CellFrame;

public final class MeshScratch {
    private final CellVoxels voxels = new CellVoxels();
    private final MeshBuffer buffer = new MeshBuffer();
    private final RowMasks masks = new RowMasks();
    private final FacePlane negative = new FacePlane();
    private final FacePlane positive = new FacePlane();
    private final FacePlane negativeFluid = new FacePlane();
    private final FacePlane positiveFluid = new FacePlane();
    private final FacePlane negativeBorder = new FacePlane();
    private final FacePlane positiveBorder = new FacePlane();
    private final FacePlane negativeInward = new FacePlane();
    private final FacePlane positiveInward = new FacePlane();
    private final GreedyMerger merger = new GreedyMerger();
    private final TintBlend blend = new TintBlend();
    private final VoxelPlacements placements = new VoxelPlacements();
    private final VoxelModels voxelModels = new VoxelModels();

    public CellVoxels voxels() {
        return voxels;
    }

    public MeshBuffer buffer() {
        return buffer;
    }

    public RowMasks masks() {
        return masks;
    }

    public FacePlane negativePlane() {
        return negative;
    }

    public FacePlane positivePlane() {
        return positive;
    }

    public FacePlane negativeFluidPlane() {
        return negativeFluid;
    }

    public FacePlane positiveFluidPlane() {
        return positiveFluid;
    }

    public FacePlane negativeBorderPlane() {
        return negativeBorder;
    }

    public FacePlane positiveBorderPlane() {
        return positiveBorder;
    }

    public FacePlane negativeInwardPlane() {
        return negativeInward;
    }

    public FacePlane positiveInwardPlane() {
        return positiveInward;
    }

    public GreedyMerger merger() {
        return merger;
    }

    public TintBlend blend() {
        return blend;
    }

    public VoxelPlacements placements() {
        return placements;
    }

    public VoxelModels voxelModels() {
        return voxelModels;
    }

    public void begin(CellFrame frame, long key, BiomeTints tints, int blendRadius) {
        buffer.reset();
        placements.begin(frame, key);
        voxelModels.begin(frame, key);
        blend.begin(voxels, tints, key, blendRadius);
    }

    public void reset() {
        buffer.reset();
    }
}
