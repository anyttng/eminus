package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

class FluidCornersTest {
    private static final int LAST = DetailLevel.VOXELS_PER_SIDE - 1;
    private static final int LAVA_FLUID = 1;
    private static final int SOURCE = 1;
    private static final int FLOW_SIX = 2;
    private static final int FLOW_FOUR = 3;
    private static final int STONE = 4;
    private static final float SOURCE_HEIGHT = 8.0F / 9.0F;
    private static final float SIX_HEIGHT = 6.0F / 9.0F;
    private static final float FOUR_HEIGHT = 4.0F / 9.0F;
    private static final double HEAVY = 10.0;
    private static final int Y = 5;
    private static final int Z = 8;
    private static final int SOURCE_X = 10;
    private static final int BIOME = 0;

    private final FakeModels models = new FakeModels();
    private final CellVoxels voxels = new CellVoxels();
    private final FluidCorners corners = new FluidCorners(voxels, models);

    FluidCornersTest() {
        models.holds(SOURCE, LAVA_FLUID, SOURCE_HEIGHT);
        models.holds(FLOW_SIX, LAVA_FLUID, SIX_HEIGHT);
        models.holds(FLOW_FOUR, LAVA_FLUID, FOUR_HEIGHT);
        models.makeSolid(STONE);
    }

    @Test
    void aSourceInOpenAirSlopesEveryCornerTowardsTheAirAroundIt() {
        Cell cell = blank();
        cell.set(SOURCE_X, Y, Z, block(SOURCE));
        voxels.load(cell);

        int shape = corners.of(SOURCE, SOURCE_X, Y, Z);

        int expected = FluidCorners.step(SOURCE_HEIGHT * HEAVY / (HEAVY + 2.0));
        assertEquals(expected, FluidCorners.northWest(shape));
        assertEquals(expected, FluidCorners.northEast(shape));
        assertEquals(expected, FluidCorners.southWest(shape));
        assertEquals(expected, FluidCorners.southEast(shape));
    }

    @Test
    void aFlowInATroughAveragesEachCornerWithItsNeighbourAlongTheTrough() {
        voxels.load(trough());

        int shape = corners.of(FLOW_SIX, SOURCE_X + 1, Y, Z);

        int upstream = FluidCorners.step(((double) SIX_HEIGHT + SOURCE_HEIGHT * HEAVY) / (1.0 + HEAVY));
        int downstream = FluidCorners.step(((double) SIX_HEIGHT + FOUR_HEIGHT) / 2.0);
        assertEquals(upstream, FluidCorners.northWest(shape));
        assertEquals(upstream, FluidCorners.southWest(shape));
        assertEquals(downstream, FluidCorners.northEast(shape));
        assertEquals(downstream, FluidCorners.southEast(shape));
    }

    @Test
    void twoFlowsShareTheCornersBetweenThem() {
        voxels.load(trough());

        int upstream = corners.of(FLOW_SIX, SOURCE_X + 1, Y, Z);
        int downstream = corners.of(FLOW_FOUR, SOURCE_X + 2, Y, Z);

        assertEquals(FluidCorners.northEast(upstream), FluidCorners.northWest(downstream));
        assertEquals(FluidCorners.southEast(upstream), FluidCorners.southWest(downstream));
    }

    @Test
    void aFluidUnderTheSameFluidReachesTheTopOnEveryCorner() {
        Cell cell = blank();
        cell.set(SOURCE_X, Y, Z, block(FLOW_SIX));
        cell.set(SOURCE_X, Y + 1, Z, block(SOURCE));
        voxels.load(cell);

        assertTrue(FluidCorners.full(corners.of(FLOW_SIX, SOURCE_X, Y, Z)));
    }

    @Test
    void aSourceAmongSourcesStaysFlat() {
        Cell cell = blank();
        for (int x = SOURCE_X - 1; x <= SOURCE_X + 1; x++) {
            for (int z = Z - 1; z <= Z + 1; z++) {
                cell.set(x, Y, z, block(SOURCE));
            }
        }
        voxels.load(cell);

        assertEquals(FluidCorners.FLAT, corners.of(SOURCE, SOURCE_X, Y, Z));
    }

    @Test
    void aCornerOnTheCellEdgeReadsTheDiagonalCell() {
        Cell cell = blank();
        cell.set(LAST, Y, LAST, block(FLOW_SIX));
        voxels.load(cell);
        Cell east = blank();
        east.set(0, Y, LAST, block(FLOW_SIX));
        voxels.loadNeighbour(Direction.EAST, east);
        Cell diagonal = blank();
        diagonal.set(0, Y, 0, block(FLOW_SIX));
        diagonal.set(0, Y + 1, 0, block(FLOW_SIX));
        voxels.loadDiagonal(1, 1, diagonal);

        int shape = corners.of(FLOW_SIX, LAST, Y, LAST);

        assertEquals(FluidCorners.STEPS, FluidCorners.southEast(shape));
        assertTrue(FluidCorners.northEast(shape) < FluidCorners.STEPS);
    }

    @Test
    void aTopLayerCornerReadsTheCellAboveItsEdge() {
        Cell cell = blank();
        cell.set(LAST, LAST, Z, block(FLOW_SIX));
        voxels.load(cell);
        Cell east = blank();
        east.set(0, LAST, Z, block(FLOW_SIX));
        voxels.loadNeighbour(Direction.EAST, east);
        Cell aboveEast = blank();
        aboveEast.set(0, 0, Z, block(FLOW_SIX));
        voxels.loadAboveSide(Direction.EAST, aboveEast);

        int shape = corners.of(FLOW_SIX, LAST, LAST, Z);

        assertEquals(FluidCorners.STEPS, FluidCorners.northEast(shape));
        assertEquals(FluidCorners.STEPS, FluidCorners.southEast(shape));
        assertTrue(FluidCorners.northWest(shape) < FluidCorners.STEPS);
    }

    private static Cell trough() {
        Cell cell = blank();
        for (int x = SOURCE_X - 1; x <= SOURCE_X + 3; x++) {
            cell.set(x, Y, Z - 1, block(STONE));
            cell.set(x, Y, Z + 1, block(STONE));
        }

        cell.set(SOURCE_X - 1, Y, Z, block(STONE));
        cell.set(SOURCE_X, Y, Z, block(SOURCE));
        cell.set(SOURCE_X + 1, Y, Z, block(FLOW_SIX));
        cell.set(SOURCE_X + 2, Y, Z, block(FLOW_FOUR));
        cell.set(SOURCE_X + 3, Y, Z, block(STONE));
        return cell;
    }

    private static Cell blank() {
        return Cell.blank(CellKey.pack(0, 0, 0, 0));
    }

    private static long block(int stateId) {
        return VoxelEntry.pack(stateId, BIOME, VoxelEntry.light(VoxelEntry.MAX_LIGHT, 0));
    }
}
