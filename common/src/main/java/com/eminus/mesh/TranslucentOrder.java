package com.eminus.mesh;

import com.eminus.cell.DetailLevel;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

final class TranslucentOrder {
    private static final int FACE_SHIFT = 24;
    private static final int STEPS_PER_VOXEL = 1 << 16;
    private static final int MARGIN_VOXELS = 1;
    private static final int FARTHEST_PLANE = (DetailLevel.VOXELS_PER_SIDE + 2 * MARGIN_VOXELS) * STEPS_PER_VOXEL;
    private static final float FULL = 1.0F;

    static int unplaced(long quad) {
        return Quad.face(quad) << FACE_SHIFT;
    }

    static int of(long quad, int level, int placement, MeshModels models) {
        if (Quad.isBlade(quad)) {
            return unplaced(quad);
        }

        int face = Quad.face(quad);
        Direction direction = Direction.from3DDataValue(face);
        Direction.Axis axis = direction.getAxis();
        boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        int modelId = Quad.modelId(quad);
        int metadata = models.metadata(modelId);
        boolean fluid = ModelMetadata.has(metadata, ModelMetadata.FLUID);
        float inset = models.inset(modelId, face);

        float depth = depth(direction, positive, level, placement, inset,
                ModelMetadata.has(metadata, ModelMetadata.INWARD));
        int offset = QuadPlacement.offset(level, fluid, placement);
        depth += (float) axis.choose(QuadOffset.x(offset), QuadOffset.y(offset), QuadOffset.z(offset));

        int voxel = axis.choose(Quad.x(quad), Quad.y(quad), Quad.z(quad)) + MARGIN_VOXELS;
        int plane = voxel * STEPS_PER_VOXEL + Math.round(depth * STEPS_PER_VOXEL);
        return face << FACE_SHIFT | (positive ? plane : FARTHEST_PLANE - plane);
    }

    private static float depth(Direction direction, boolean positive, int level, int placement, float inset,
            boolean inward) {
        float blocks = DetailLevel.blocksPerVoxel(level);
        if (direction == Direction.DOWN) {
            return (QuadPlacement.lowGap(level, placement) + inset) / blocks;
        }

        if (direction == Direction.UP) {
            return (blocks - QuadPlacement.highGap(level, placement) - inset) / blocks;
        }

        float side = positive ? FULL - inset : inset;
        return inward ? FULL - side : side;
    }

    private TranslucentOrder() {
    }
}
