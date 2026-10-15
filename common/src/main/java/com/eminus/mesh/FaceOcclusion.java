package com.eminus.mesh;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

public final class FaceOcclusion {
    public static final Direction BLADE_FACING = Direction.NORTH;

    private static final float FLUSH = 1.0E-4F;
    private static final int LOW = -1;
    private static final int HIGH = 1;
    private static final Direction[] FIRST_CORNER = firstCorners();

    private final CellVoxels voxels;
    private final MeshModels models;
    private final boolean finest;

    private int baseX;
    private int baseY;
    private int baseZ;
    private int reachX;
    private int reachY;
    private int reachZ;
    private int uX;
    private int uY;
    private int uZ;
    private int vX;
    private int vY;
    private int vZ;

    public FaceOcclusion(CellVoxels voxels, MeshModels models, int level) {
        this.voxels = voxels;
        this.models = models;
        finest = level == DetailLevel.MIN;
    }

    public int face(Direction face, int x, int y, int z, int stateId, int metadata, float inset) {
        if (!ModelMetadata.has(metadata, ModelMetadata.SMOOTH)) {
            return OcclusionWord.NONE;
        }

        boolean cubic = !ModelMetadata.has(metadata, ModelMetadata.SLOPED)
                && (inset < FLUSH || StateShade.has(models.shade(stateId), StateShade.FULL_COLLISION));
        return word(face, x, y, z, cubic);
    }

    public int blade(int x, int y, int z, int metadata) {
        return ModelMetadata.has(metadata, ModelMetadata.SMOOTH)
                ? word(BLADE_FACING, x, y, z, false)
                : OcclusionWord.NONE;
    }

    private int word(Direction face, int x, int y, int z, boolean cubic) {
        int lift = cubic ? 1 : 0;
        baseX = x + face.getStepX() * lift;
        baseY = y + face.getStepY() * lift;
        baseZ = z + face.getStepZ() * lift;
        int reach = finest || !cubic ? 1 : 0;
        reachX = face.getStepX() * reach;
        reachY = face.getStepY() * reach;
        reachZ = face.getStepZ() * reach;

        Direction.Axis normal = face.getAxis();
        Direction.Axis width = PlaneAxes.width(normal);
        Direction.Axis height = PlaneAxes.height(normal);
        uX = width == Direction.Axis.X ? 1 : 0;
        uY = width == Direction.Axis.Y ? 1 : 0;
        uZ = width == Direction.Axis.Z ? 1 : 0;
        vX = height == Direction.Axis.X ? 1 : 0;
        vY = height == Direction.Axis.Y ? 1 : 0;
        vZ = height == Direction.Axis.Z ? 1 : 0;

        boolean lowU = dark(LOW, 0);
        boolean highU = dark(HIGH, 0);
        boolean lowV = dark(0, LOW);
        boolean highV = dark(0, HIGH);
        Direction first = FIRST_CORNER[face.get3DDataValue()];
        boolean firstDark = first.getAxis() == width
                ? first.getAxisDirection() == Direction.AxisDirection.POSITIVE ? highU : lowU
                : first.getAxisDirection() == Direction.AxisDirection.POSITIVE ? highV : lowV;

        return (dark(0, 0) ? OcclusionWord.FRONT : 0)
                | (lowU ? OcclusionWord.LOW_U : 0)
                | (highU ? OcclusionWord.HIGH_U : 0)
                | (lowV ? OcclusionWord.LOW_V : 0)
                | (highV ? OcclusionWord.HIGH_V : 0)
                | (corner(LOW, LOW, firstDark) ? OcclusionWord.LOW_U_LOW_V : 0)
                | (corner(HIGH, LOW, firstDark) ? OcclusionWord.HIGH_U_LOW_V : 0)
                | (corner(LOW, HIGH, firstDark) ? OcclusionWord.LOW_U_HIGH_V : 0)
                | (corner(HIGH, HIGH, firstDark) ? OcclusionWord.HIGH_U_HIGH_V : 0);
    }

    private boolean corner(int alongU, int alongV, boolean firstDark) {
        if (!permeable(alongU, 0) && !permeable(0, alongV)) {
            return firstDark;
        }

        return dark(alongU, alongV);
    }

    private boolean dark(int alongU, int alongV) {
        long entry = voxels.entry(baseX + uX * alongU + vX * alongV, baseY + uY * alongU + vY * alongV,
                baseZ + uZ * alongU + vZ * alongV);
        return whole(entry) && StateShade.has(models.shade(VoxelEntry.state(entry)), StateShade.DARK);
    }

    private boolean permeable(int alongU, int alongV) {
        long entry = voxels.entry(baseX + reachX + uX * alongU + vX * alongV,
                baseY + reachY + uY * alongU + vY * alongV, baseZ + reachZ + uZ * alongU + vZ * alongV);
        return !whole(entry) || StateShade.has(models.shade(VoxelEntry.state(entry)), StateShade.PERMEABLE);
    }

    private static boolean whole(long entry) {
        return !VoxelEntry.isAir(entry) && VoxelEntry.gaps(entry) == VoxelEntry.NO_GAPS;
    }

    private static Direction[] firstCorners() {
        Direction[] first = new Direction[Direction.values().length];
        first[Direction.DOWN.get3DDataValue()] = Direction.WEST;
        first[Direction.UP.get3DDataValue()] = Direction.EAST;
        first[Direction.NORTH.get3DDataValue()] = Direction.UP;
        first[Direction.SOUTH.get3DDataValue()] = Direction.WEST;
        first[Direction.WEST.get3DDataValue()] = Direction.UP;
        first[Direction.EAST.get3DDataValue()] = Direction.DOWN;
        return first;
    }
}
