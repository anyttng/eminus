package com.eminus.mesh;

public final class MeshScratch {
    private final CellVoxels voxels = new CellVoxels();
    private final MeshBuffer buffer = new MeshBuffer();
    private final RowMasks masks = new RowMasks();
    private final FacePlane negative = new FacePlane();
    private final FacePlane positive = new FacePlane();
    private final FacePlane negativeFluid = new FacePlane();
    private final FacePlane positiveFluid = new FacePlane();
    private final GreedyMerger merger = new GreedyMerger();
    private final TintBlend blend = new TintBlend();
    private final VoxelOffsets offsets = new VoxelOffsets();

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

    public GreedyMerger merger() {
        return merger;
    }

    public TintBlend blend() {
        return blend;
    }

    public VoxelOffsets offsets() {
        return offsets;
    }

    public void reset() {
        buffer.reset();
    }
}
