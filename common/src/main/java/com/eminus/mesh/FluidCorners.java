package com.eminus.mesh;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;

public final class FluidCorners {
    public static final int LEVEL = DetailLevel.MIN;
    public static final int FLAT = 0;
    public static final int STEPS = 255;

    private static final int BITS = 8;
    private static final int MASK = (1 << BITS) - 1;
    private static final int NORTH_WEST_SHIFT = 0;
    private static final int NORTH_EAST_SHIFT = BITS;
    private static final int SOUTH_WEST_SHIFT = 2 * BITS;
    private static final int SOUTH_EAST_SHIFT = 3 * BITS;

    private static final double FULL = 1.0;
    private static final double OPEN = 0.0;
    private static final double BLOCKED = -1.0;
    private static final double HEAVY_FROM = 0.8;
    private static final double HEAVY_WEIGHT = 10.0;
    private static final double LIGHT_WEIGHT = 1.0;

    private final CellVoxels voxels;
    private final MeshModels models;

    private int fluid;
    private double weightedSum;
    private double weights;

    public FluidCorners(CellVoxels voxels, MeshModels models) {
        this.voxels = voxels;
        this.models = models;
    }

    public int of(int stateId, int x, int y, int z) {
        fluid = stateId;
        double self = height(x, y, z);
        int flat = step(models.fluidHeight(stateId));

        int northWest;
        int northEast;
        int southWest;
        int southEast;
        if (self >= FULL) {
            northWest = northEast = southWest = southEast = STEPS;
        } else {
            double north = height(x, y, z - 1);
            double south = height(x, y, z + 1);
            double west = height(x - 1, y, z);
            double east = height(x + 1, y, z);
            northWest = step(average(self, north, west, x - 1, y, z - 1));
            northEast = step(average(self, north, east, x + 1, y, z - 1));
            southWest = step(average(self, south, west, x - 1, y, z + 1));
            southEast = step(average(self, south, east, x + 1, y, z + 1));
        }

        if (northWest == flat && northEast == flat && southWest == flat && southEast == flat) {
            return FLAT;
        }

        return northWest << NORTH_WEST_SHIFT | northEast << NORTH_EAST_SHIFT
                | southWest << SOUTH_WEST_SHIFT | southEast << SOUTH_EAST_SHIFT;
    }

    public static int northWest(int corners) {
        return corners >>> NORTH_WEST_SHIFT & MASK;
    }

    public static int northEast(int corners) {
        return corners >>> NORTH_EAST_SHIFT & MASK;
    }

    public static int southWest(int corners) {
        return corners >>> SOUTH_WEST_SHIFT & MASK;
    }

    public static int southEast(int corners) {
        return corners >>> SOUTH_EAST_SHIFT & MASK;
    }

    public static boolean full(int corners) {
        return corners != FLAT && northWest(corners) == STEPS && northEast(corners) == STEPS
                && southWest(corners) == STEPS && southEast(corners) == STEPS;
    }

    public static int step(double height) {
        return (int) Math.round(height * STEPS);
    }

    // Summed in double, so the two voxels sharing a corner reach the same bits although they add in another order.
    private double average(double self, double first, double second, int cornerX, int y, int cornerZ) {
        if (first >= FULL || second >= FULL) {
            return FULL;
        }

        weightedSum = 0.0;
        weights = 0.0;
        if (first > OPEN || second > OPEN) {
            double corner = height(cornerX, y, cornerZ);
            if (corner >= FULL) {
                return FULL;
            }

            weigh(corner);
        }

        weigh(self);
        weigh(first);
        weigh(second);
        return weightedSum / weights;
    }

    private void weigh(double height) {
        if (height >= HEAVY_FROM) {
            weightedSum += height * HEAVY_WEIGHT;
            weights += HEAVY_WEIGHT;
        } else if (height >= OPEN) {
            weightedSum += height;
            weights += LIGHT_WEIGHT;
        }
    }

    private double height(int x, int y, int z) {
        long entry = voxels.around(x, y, z);
        if (VoxelEntry.isAir(entry)) {
            return OPEN;
        }

        int stateId = VoxelEntry.state(entry);
        if (models.sameFluid(fluid, stateId)) {
            long above = voxels.around(x, y + 1, z);
            return !VoxelEntry.isAir(above) && models.sameFluid(fluid, VoxelEntry.state(above))
                    ? FULL
                    : models.fluidHeight(stateId);
        }

        return models.solid(stateId) ? BLOCKED : OPEN;
    }
}
