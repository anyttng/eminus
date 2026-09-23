package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.Cell;
import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.FaceMask;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.StateTable;
import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CellMesherTest {
    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int LAST = SIDE - 1;

    private static final int AIR = VoxelEntry.AIR_STATE_ID;
    private static final int STONE = 1;
    private static final int GLASS = 2;
    private static final int LAVA = 3;
    private static final int TINT_A = 4;
    private static final int TINT_B = 5;
    private static final int WATER = 6;
    private static final int WATERLOGGED = 7;
    private static final int WET_LEAVES = 8;
    private static final int GRASS = 9;
    private static final int OPAQUE_LEAVES = 19;
    private static final int CUTOUT_LEAVES = 20;
    private static final int GRASS_BLOCK = 23;
    private static final int VARIED = 26;
    private static final int FLOWING_WATER = 28;

    private static final int STONE_MODEL = 10;
    private static final int GLASS_MODEL = 11;
    private static final int LAVA_MODEL = 12;
    private static final int TINT_A_MODEL = 13;
    private static final int TINT_B_MODEL = 14;
    private static final int WATER_MODEL = 15;
    private static final int WATERLOGGED_MODEL = 16;
    private static final int WET_LEAVES_MODEL = 17;
    private static final int GRASS_MODEL = 18;
    private static final int OPAQUE_LEAVES_MODEL = 21;
    private static final int CUTOUT_LEAVES_MODEL = 22;
    private static final int GRASS_BLOCK_MODEL = 24;
    private static final int SUBMERGED_WATER_MODEL = 25;
    private static final int VARIED_MODEL = 27;
    private static final int FLOWING_WATER_MODEL = 29;
    private static final int SOURCE_LAVA = 30;
    private static final int FLOWING_LAVA = 31;
    private static final int SOURCE_LAVA_MODEL = 32;
    private static final int FLOWING_LAVA_MODEL = 33;
    private static final int SUBMERGED_LAVA_MODEL = 34;
    private static final int HEADED = 35;
    private static final int HEADED_MODEL = 36;
    private static final int HEAD_FACES = 2;
    private static final int HEADED_VOXELS = 3;
    private static final int VARIED_ROW = 4;
    private static final int GRASS_ROW = 0;
    private static final int PLAINS = 5;
    private static final int PLAINS_GREEN = 0x91BD59;
    private static final int SWAMP_GREEN = 0x6A7039;
    private static final int PLAINS_WIDTH = 8;
    private static final int NO_BLEND = 0;

    private static final int BIOME = 3;
    private static final int FULL_SKY = 15;
    private static final int NO_BLOCK_LIGHT = 0;
    private static final int GRADIENT_BASE = 12;
    private static final int GRADIENT_SPAN = 4;
    private static final int TORCH_BLOCK_LIGHT = 14;
    private static final int GLASS_FACES_IN_AIR = 5;
    private static final int CUBE_SIDE = 2;
    private static final int CUBE_FACES = 6;
    private static final int CANOPY_SIDE = 3;
    private static final int SEE_THROUGH_DAMPENING = 1;
    private static final int NEIGHBOUR_X = 2;
    private static final int WATERLOGGED_FACES = QuadGroups.DIRECTIONAL_COUNT * 2;
    private static final int LAST_IN_FIRST_CHUNK = 15;
    private static final long FIRST_CHUNK = ColumnCoverage.pack(0, 0);
    private static final long SECOND_CHUNK = ColumnCoverage.pack(1, 0);
    private static final int NO_QUAD = -1;
    private static final int MIN_BLOCK_Y = -64;
    private static final CellFrame FRAME = new CellFrame(MIN_BLOCK_Y);
    private static final float OFFSET_TOLERANCE = 1.0F / 512.0F;
    private static final int COARSE_LEVEL = 1;
    private static final int LAVA_FLUID = 1;
    private static final float SOURCE_HEIGHT = 8.0F / 9.0F;
    private static final float FLOWING_HEIGHT = 6.0F / 9.0F;

    private final Map<Integer, Integer> opacities = new HashMap<>();
    private final FakeModels models = new FakeModels();
    private final FakeTints tints = new FakeTints();
    private final StateOpacity opacity = stateId -> opacities.getOrDefault(stateId, 0);

    private int bakeRequests;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void oneCubeInEmptyAirShowsOneQuadPerFaceGroup() {
        defineBlocks();
        Cell cell = blank();
        cell.set(16, 16, 16, block(STONE));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertEquals(QuadGroups.DIRECTIONAL_COUNT, mesh.quadCount());
        for (int group = 0; group < QuadGroups.DIRECTIONAL_COUNT; group++) {
            assertEquals(1, mesh.groupCount(group));
        }

        assertEquals(0, mesh.groupCount(QuadGroups.DOUBLE_SIDED));
        assertEquals(0, mesh.groupCount(QuadGroups.TRANSLUCENT));
    }

    @Test
    void aFullFloorShowsFourQuadsOnTopAndNothingElse() {
        defineBlocks();
        CellMesh mesh = mesh(floor(), ground(), 0);

        assertEquals(4, mesh.groupCount(Direction.UP.ordinal()));
        assertEquals(4, mesh.quadCount());

        for (int index = 0; index < mesh.quadCount(); index++) {
            long quad = mesh.quad(index);
            assertEquals(Quad.MAX_SIDE, Quad.width(quad));
            assertEquals(Quad.MAX_SIDE, Quad.height(quad));
        }
    }

    @Test
    void theFaceBetweenStoneAndGlassBelongsToStoneAndTheGlassSideIsDropped() {
        defineBlocks();
        Cell cell = blank();
        cell.set(10, 10, 10, block(STONE));
        cell.set(11, 10, 10, block(GLASS));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertTrue(has(mesh, Direction.EAST, 10, 10, 10, STONE_MODEL));
        assertTrue(absent(mesh, Direction.WEST, 11, 10, 10));
        assertEquals(QuadGroups.DIRECTIONAL_COUNT + GLASS_FACES_IN_AIR, mesh.quadCount());
    }

    @Test
    void lavaCarriesItsEmissionThroughADarkNeighbour() {
        defineBlocks();
        Cell cell = blank();
        cell.set(4, 4, 4, block(LAVA));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertEquals(QuadGroups.DIRECTIONAL_COUNT, mesh.quadCount());
        for (int index = 0; index < mesh.quadCount(); index++) {
            assertEquals(VoxelEntry.light(FULL_SKY, ModelMetadata.MAX_EMISSION), Quad.light(mesh.quad(index)));
        }
    }

    @Test
    void aLightGradientSplitsTheFloorAtEveryLevel() {
        defineBlocks();
        CellMesh fine = mesh(litFloor(), ground(), 0);
        CellMesh coarse = mesh(litFloor(), ground(), 2);

        assertEquals(SIDE * 2, fine.groupCount(Direction.UP.ordinal()));
        assertEquals(SIDE * 2, coarse.groupCount(Direction.UP.ordinal()));
    }

    @Test
    void torchLightReachesTheFloorAtLevelTwo() {
        defineBlocks();
        Cell cell = floor();
        for (int z = 0; z < SIDE; z++) {
            for (int x = 0; x < SIDE; x++) {
                cell.set(x, 1, z, VoxelEntry.pack(AIR, BIOME, VoxelEntry.light(FULL_SKY, TORCH_BLOCK_LIGHT)));
            }
        }

        CellMesh mesh = mesh(cell, ground(), 2);

        assertEquals(4, mesh.groupCount(Direction.UP.ordinal()));
        for (int index = 0; index < mesh.quadCount(); index++) {
            long quad = mesh.quad(index);
            if (Quad.face(quad) == Direction.UP.ordinal()) {
                assertEquals(VoxelEntry.light(FULL_SKY, TORCH_BLOCK_LIGHT), Quad.light(quad));
            }
        }
    }

    @Test
    void anUnbakedStateAbortsTheMeshAndAsksForTheBake() {
        defineBlocks();
        models.unbake(STONE);
        Cell cell = blank();
        cell.set(1, 1, 1, block(STONE));

        assertNull(mesh(cell, airAround(), 0));
        assertEquals(1, models.requests());
    }

    @Test
    void aGroupPastItsCapTruncates() {
        defineBlocks();
        Cell cell = blank();

        for (int y = 0; y < SIDE; y++) {
            for (int z = 0; z < SIDE; z++) {
                for (int x = 0; x < SIDE; x++) {
                    cell.set(x, y, z, block((x + y + z) % 2 == 0 ? TINT_A : TINT_B));
                }
            }
        }

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertNotNull(mesh);
        assertEquals(MeshBuffer.MAX_QUADS_PER_GROUP, mesh.groupCount(QuadGroups.TRANSLUCENT));
    }

    @Test
    void aSolidBlockOfOneTranslucentModelShowsItsOuterShellAlone() {
        defineBlocks();
        Cell cell = blank();

        for (int y = 0; y < CUBE_SIDE; y++) {
            for (int z = 0; z < CUBE_SIDE; z++) {
                for (int x = 0; x < CUBE_SIDE; x++) {
                    cell.set(x, y, z, block(TINT_A));
                }
            }
        }

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertNotNull(mesh);
        assertEquals(CUBE_FACES, mesh.groupCount(QuadGroups.TRANSLUCENT));
    }

    @Test
    void aFaceBetweenTwoDifferentTranslucentModelsSurvives() {
        defineBlocks();
        Cell cell = blank();
        cell.set(1, 1, 1, block(TINT_A));
        cell.set(NEIGHBOUR_X, 1, 1, block(TINT_B));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertNotNull(mesh);
        assertTrue(has(mesh, Direction.EAST, 1, 1, 1, TINT_A_MODEL));
    }

    @Test
    void anUnbakedNeighbourAbortsTheMeshAndAsksForTheBake() {
        defineBlocks();
        models.unbake(GLASS);
        Cell cell = blank();
        cell.set(1, 1, 1, block(TINT_A));
        cell.set(NEIGHBOUR_X, 1, 1, block(GLASS));

        assertNull(mesh(cell, airAround(), 0));
        assertEquals(1, models.requests());
    }

    @Test
    void aWaterloggedBlockShowsItsOwnFacesAndTheWaters() {
        defineBlocks();
        Cell cell = blank();
        cell.set(5, 5, 5, block(WATERLOGGED));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertEquals(WATERLOGGED_FACES, mesh.quadCount());
        assertEquals(QuadGroups.DIRECTIONAL_COUNT, mesh.groupCount(QuadGroups.TRANSLUCENT));
        assertTrue(has(mesh, Direction.EAST, 5, 5, 5, WATERLOGGED_MODEL));
        assertTrue(has(mesh, Direction.EAST, 5, 5, 5, WATER_MODEL));
    }

    @Test
    void aPureWaterBlockAsksForNoSecondModel() {
        defineBlocks();
        Cell cell = blank();
        cell.set(5, 5, 5, block(WATER));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertEquals(QuadGroups.DIRECTIONAL_COUNT, mesh.quadCount());
        assertEquals(QuadGroups.DIRECTIONAL_COUNT, mesh.groupCount(QuadGroups.TRANSLUCENT));
    }

    @Test
    void twoWaterloggedNeighboursShareNoWaterSurface() {
        defineBlocks();
        Cell cell = blank();
        cell.set(1, 1, 1, block(WATERLOGGED));
        cell.set(NEIGHBOUR_X, 1, 1, block(WATERLOGGED));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertFalse(has(mesh, Direction.EAST, 1, 1, 1, WATER_MODEL));
        assertFalse(has(mesh, Direction.WEST, NEIGHBOUR_X, 1, 1, WATER_MODEL));
        assertTrue(has(mesh, Direction.WEST, 1, 1, 1, WATER_MODEL));
        assertTrue(has(mesh, Direction.EAST, NEIGHBOUR_X, 1, 1, WATER_MODEL));
    }

    @Test
    void aWaterBlockShowsNoSurfaceTowardsAWaterloggedNeighbour() {
        defineBlocks();
        Cell cell = blank();
        cell.set(1, 1, 1, block(WATERLOGGED));
        cell.set(NEIGHBOUR_X, 1, 1, block(WATER));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertFalse(has(mesh, Direction.WEST, NEIGHBOUR_X, 1, 1, WATER_MODEL));
        assertFalse(has(mesh, Direction.EAST, 1, 1, 1, WATER_MODEL));
        assertTrue(has(mesh, Direction.EAST, NEIGHBOUR_X, 1, 1, WATER_MODEL));
    }

    @Test
    void anOpaqueWaterloggedBlockStillShowsItsWater() {
        defineBlocks();
        Cell cell = blank();
        cell.set(5, 5, 5, block(WET_LEAVES));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertEquals(WATERLOGGED_FACES, mesh.quadCount());
        assertEquals(QuadGroups.DIRECTIONAL_COUNT, mesh.groupCount(QuadGroups.TRANSLUCENT));
    }

    @Test
    void anUnbakedFluidAbortsTheMeshAndAsksForTheBake() {
        defineBlocks();
        models.unbakeFluid(WATERLOGGED);
        Cell cell = blank();
        cell.set(1, 1, 1, block(WATERLOGGED));

        assertNull(mesh(cell, airAround(), 0));
        assertEquals(1, models.requests());
    }

    @Test
    void aBladedVoxelEmitsTwoBladesAndNoneOfTheSixFaces() {
        defineBlocks();
        Cell cell = blank();
        cell.set(5, 6, 7, block(GRASS));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertEquals(Quad.BLADE_COUNT, mesh.quadCount());
        assertEquals(Quad.BLADE_COUNT, mesh.groupCount(QuadGroups.DOUBLE_SIDED));
        for (int blade = 0; blade < Quad.BLADE_COUNT; blade++) {
            assertTrue(hasBlade(mesh, blade, 5, 6, 7, GRASS_MODEL), "blade " + blade);
        }
    }

    @Test
    void aBladedVoxelWithSideFacesEmitsThemBesideItsBlades() {
        defineBlocks();
        Cell cell = blank();
        cell.set(5, 6, 7, block(HEADED));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertEquals(Quad.BLADE_COUNT + HEAD_FACES, mesh.quadCount());
        assertTrue(has(mesh, Direction.EAST, 5, 6, 7, HEADED_MODEL));
        assertTrue(has(mesh, Direction.WEST, 5, 6, 7, HEADED_MODEL));
        for (int blade = 0; blade < Quad.BLADE_COUNT; blade++) {
            assertTrue(hasBlade(mesh, blade, 5, 6, 7, HEADED_MODEL), "blade " + blade);
        }
    }

    @Test
    void slopedFacesOfNeighbouringVoxelsNeverMerge() {
        defineBlocks();
        Cell cell = blank();
        cell.set(5, 6, 7, block(HEADED));
        cell.set(5, 6, 8, block(HEADED));
        cell.set(5, 7, 7, block(HEADED));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertEquals(HEADED_VOXELS * (Quad.BLADE_COUNT + HEAD_FACES), mesh.quadCount());
        assertTrue(has(mesh, Direction.EAST, 5, 6, 8, HEADED_MODEL));
        assertTrue(has(mesh, Direction.EAST, 5, 7, 7, HEADED_MODEL));
    }

    @Test
    void everyClassCutsTowardsAColumnNeverIngestedUntilItsNeighbourLands() {
        defineBlocks();
        Cell cell = blank();
        cell.set(LAST_IN_FIRST_CHUNK, 16, 5, block(STONE));
        cell.set(LAST_IN_FIRST_CHUNK, 16, 9, block(WATER));
        cell.set(LAST_IN_FIRST_CHUNK, 16, 13, block(GLASS));

        CellMesh uncovered = mesh(cell, airAround(), coverage(FIRST_CHUNK));

        assertEquals(VoxelEntry.light(FULL_SKY, NO_BLOCK_LIGHT),
                lightAt(uncovered, Direction.EAST, LAST_IN_FIRST_CHUNK, 16, 5, STONE_MODEL));
        assertEquals(VoxelEntry.light(FULL_SKY, NO_BLOCK_LIGHT),
                lightAt(uncovered, Direction.EAST, LAST_IN_FIRST_CHUNK, 16, 9, WATER_MODEL));
        assertEquals(VoxelEntry.light(FULL_SKY, NO_BLOCK_LIGHT),
                lightAt(uncovered, Direction.EAST, LAST_IN_FIRST_CHUNK, 16, 13, GLASS_MODEL));

        cell.set(LAST_IN_FIRST_CHUNK + 1, 16, 5, block(STONE));
        cell.set(LAST_IN_FIRST_CHUNK + 1, 16, 9, block(WATER));
        CellMesh covered = mesh(cell, airAround(), coverage(FIRST_CHUNK, SECOND_CHUNK));

        assertFalse(has(covered, Direction.EAST, LAST_IN_FIRST_CHUNK, 16, 5, STONE_MODEL));
        assertFalse(has(covered, Direction.EAST, LAST_IN_FIRST_CHUNK, 16, 9, WATER_MODEL));
        assertTrue(has(covered, Direction.EAST, LAST_IN_FIRST_CHUNK, 16, 13, GLASS_MODEL));
    }

    @Test
    void waterBelowTheSameFluidTakesItsSubmergedModelAndOnlyTheSurfaceVoxelStopsShort() {
        defineBlocks();
        Cell cell = blank();
        cell.set(4, 8, 4, block(WATERLOGGED));
        cell.set(4, 9, 4, block(WATER));
        cell.set(4, 10, 4, block(WATER));
        cell.set(4, 11, 4, block(WATER));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertTrue(has(mesh, Direction.EAST, 4, 11, 4, WATER_MODEL));
        assertTrue(has(mesh, Direction.EAST, 4, 9, 4, SUBMERGED_WATER_MODEL));
        assertTrue(has(mesh, Direction.EAST, 4, 8, 4, SUBMERGED_WATER_MODEL));
        assertFalse(has(mesh, Direction.EAST, 4, 9, 4, WATER_MODEL));
        assertFalse(has(mesh, Direction.EAST, 4, 8, 4, WATER_MODEL));
        assertTrue(absent(mesh, Direction.UP, 4, 10, 4));
    }

    @Test
    void twoLevelsOfOneFluidMeetWithoutAFaceAndTheLowerOneTakesItsSubmergedModel() {
        defineBlocks();
        Cell cell = blank();
        cell.set(4, 9, 4, block(FLOWING_WATER));
        cell.set(4, 10, 4, block(WATER));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertTrue(has(mesh, Direction.EAST, 4, 9, 4, SUBMERGED_WATER_MODEL));
        assertFalse(has(mesh, Direction.EAST, 4, 9, 4, FLOWING_WATER_MODEL));
        assertTrue(absent(mesh, Direction.UP, 4, 9, 4));
        assertTrue(has(mesh, Direction.EAST, 4, 10, 4, WATER_MODEL));
    }

    @Test
    void glassBesideALavaSurfaceKeepsTheFaceTheyShare() {
        defineLava();
        Cell cell = blank();
        cell.set(4, 4, 4, block(GLASS));
        cell.set(5, 4, 4, block(SOURCE_LAVA));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertTrue(has(mesh, Direction.EAST, 4, 4, 4, GLASS_MODEL));
    }

    @Test
    void glassBesideSubmergedLavaLosesTheFaceTheyShare() {
        defineLava();
        Cell cell = blank();
        cell.set(4, 4, 4, block(GLASS));
        cell.set(5, 4, 4, block(SOURCE_LAVA));
        cell.set(5, 5, 4, block(SOURCE_LAVA));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertTrue(absent(mesh, Direction.EAST, 4, 4, 4));
    }

    @Test
    void twoLavaSurfacesOfOneLevelShareNoFace() {
        defineLava();
        Cell cell = blank();
        cell.set(4, 4, 4, block(SOURCE_LAVA));
        cell.set(5, 4, 4, block(SOURCE_LAVA));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertTrue(absent(mesh, Direction.EAST, 4, 4, 4));
        assertTrue(absent(mesh, Direction.WEST, 5, 4, 4));
    }

    @Test
    void aLavaSourceKeepsItsSideAboveALowerFlowingLevelOnACoarseLevel() {
        defineLava();
        Cell cell = blank();
        cell.set(4, 4, 4, block(SOURCE_LAVA));
        cell.set(5, 4, 4, block(FLOWING_LAVA));

        CellMesh mesh = mesh(cell, airAround(), COARSE_LEVEL);

        assertTrue(has(mesh, Direction.EAST, 4, 4, 4, SOURCE_LAVA_MODEL));
        assertEquals(FluidCorners.FLAT, cornersOf(mesh, Direction.UP, 4, 4, 4));
    }

    @Test
    void aLavaSourceSlopesIntoALowerFlowingLevelWithNoFaceBetweenThem() {
        defineLava();
        Cell cell = blank();
        cell.set(4, 4, 4, block(SOURCE_LAVA));
        cell.set(5, 4, 4, block(FLOWING_LAVA));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertTrue(absent(mesh, Direction.EAST, 4, 4, 4));
        assertTrue(absent(mesh, Direction.WEST, 5, 4, 4));
        int source = cornersOf(mesh, Direction.UP, 4, 4, 4);
        int flow = cornersOf(mesh, Direction.UP, 5, 4, 4);
        assertEquals(FluidCorners.northEast(source), FluidCorners.northWest(flow));
        assertEquals(FluidCorners.southEast(source), FluidCorners.southWest(flow));
        assertTrue(FluidCorners.northWest(source) > FluidCorners.northEast(source));
        assertTrue(FluidCorners.northWest(flow) > FluidCorners.northEast(flow));
    }

    @Test
    void aLavaSideTowardsAirFollowsTheCornersOfItsTop() {
        defineLava();
        Cell cell = blank();
        cell.set(4, 4, 4, block(SOURCE_LAVA));
        cell.set(5, 4, 4, block(FLOWING_LAVA));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertEquals(cornersOf(mesh, Direction.UP, 4, 4, 4), cornersOf(mesh, Direction.NORTH, 4, 4, 4));
    }

    @Test
    void aLavaTopUnderStoneWithEveryCornerFullIsHidden() {
        defineLava();
        Cell cell = blank();
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                cell.set(x, 4, z, block(SOURCE_LAVA));
                if (x != 4 || z != 4) {
                    cell.set(x, 5, z, block(SOURCE_LAVA));
                }
            }
        }
        cell.set(4, 5, 4, block(STONE));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertTrue(absent(mesh, Direction.UP, 4, 4, 4));
    }

    @Test
    void aBladedVoxelLeavesItsSolidNeighbourItsOwnFaces() {
        defineBlocks();
        Cell cell = blank();
        cell.set(5, 6, 7, block(GRASS));
        cell.set(6, 6, 7, block(STONE));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertEquals(Quad.BLADE_COUNT + CUBE_FACES, mesh.quadCount());
        assertTrue(has(mesh, Direction.WEST, 6, 6, 7, STONE_MODEL));
    }

    @Test
    void aLeafCanopyShowsItsOuterShellWhetherItsModelIsOpaqueOrCutout() {
        defineBlocks();

        assertEquals(CUBE_FACES, mesh(cube(OPAQUE_LEAVES), airAround(), 0).quadCount());
        assertEquals(CUBE_FACES, mesh(cube(CUTOUT_LEAVES), airAround(), 0).quadCount());
    }

    @Test
    void seeThroughLeavesShowTheCanopysInsideFaces() {
        defineBlocks();
        opacities.put(CUTOUT_LEAVES, SEE_THROUGH_DAMPENING);

        CellMesh mesh = mesh(cube(CUTOUT_LEAVES), airAround(), 0);

        assertEquals(CUBE_FACES * 2, mesh.quadCount());
    }

    @Test
    void aTrunkInsideSeeThroughLeavesShowsItsFacesAndOneInsidePinnedLeavesDoesNot() {
        defineBlocks();
        Cell cell = blank();
        for (int y = 0; y < CANOPY_SIDE; y++) {
            for (int z = 0; z < CANOPY_SIDE; z++) {
                for (int x = 0; x < CANOPY_SIDE; x++) {
                    cell.set(x, y, z, block(CUTOUT_LEAVES));
                }
            }
        }

        cell.set(1, 1, 1, block(STONE));

        CellMesh pinned = mesh(cell, airAround(), 0);
        opacities.put(CUTOUT_LEAVES, SEE_THROUGH_DAMPENING);
        CellMesh seeThrough = mesh(cell, airAround(), 0);

        for (Direction face : Direction.values()) {
            assertFalse(has(pinned, face, 1, 1, 1, STONE_MODEL), face.getName());
            assertTrue(has(seeThrough, face, 1, 1, 1, STONE_MODEL), face.getName());
        }
    }

    @Test
    void aTintedFloorSplitsItsQuadsWhereTheColourChangesAndCarriesBothColours() {
        defineBlocks();
        tints.define(GRASS_ROW, PLAINS, PLAINS_GREEN);
        tints.define(GRASS_ROW, BIOME, SWAMP_GREEN);
        Cell cell = blank();
        for (int z = 0; z < SIDE; z++) {
            for (int x = 0; x < SIDE; x++) {
                int biome = x < PLAINS_WIDTH ? PLAINS : BIOME;
                cell.set(x, 0, z, VoxelEntry.pack(GRASS_BLOCK, biome, VoxelEntry.light(FULL_SKY, NO_BLOCK_LIGHT)));
            }
        }

        CellMesh mesh = mesh(cell, ground(), 0);

        int up = Direction.UP.ordinal();
        assertEquals(6, mesh.groupCount(up));
        assertEquals(3, mesh.colours().length);
        for (int index = mesh.groupStart(up); index < mesh.groupStart(up) + mesh.groupCount(up); index++) {
            long quad = mesh.quad(index);
            int expected = Quad.x(quad) < PLAINS_WIDTH ? PLAINS_GREEN : SWAMP_GREEN;
            assertEquals(expected, mesh.colour(quad));
        }
    }

    @Test
    void aBladedVoxelOfAnOffsetStateCarriesTheOffsetItsBlockPositionGives() {
        defineBlocks();
        BlockState grass = Blocks.SHORT_GRASS.defaultBlockState();
        models.offsetLike(GRASS, grass);
        Cell cell = blank();
        cell.set(5, 6, 7, block(GRASS));
        cell.set(6, 6, 7, block(GRASS));

        CellMesh mesh = mesh(cell, airAround(), 0);

        Vec3 first = grass.getOffset(new BlockPos(5, MIN_BLOCK_Y + 6, 7));
        Vec3 second = grass.getOffset(new BlockPos(6, MIN_BLOCK_Y + 6, 7));
        assertNotEquals(first, second);
        assertBladesOffset(mesh, 5, 6, 7, first);
        assertBladesOffset(mesh, 6, 6, 7, second);
        assertEquals(MeshBuffer.WHITE, mesh.colour(bladeAt(mesh, 0, 5, 6, 7)));
    }

    @Test
    void aBladedVoxelOfAStateWithoutAnOffsetFunctionStaysAtTheFirstEntry() {
        defineBlocks();
        BlockState cobweb = Blocks.COBWEB.defaultBlockState();
        assertFalse(cobweb.hasOffsetFunction());
        models.offsetLike(GRASS, cobweb);
        Cell cell = blank();
        cell.set(5, 6, 7, block(GRASS));

        CellMesh mesh = mesh(cell, airAround(), 0);

        for (int blade = 0; blade < Quad.BLADE_COUNT; blade++) {
            assertEquals(MeshBuffer.UNTINTED, Quad.colourIndex(bladeAt(mesh, blade, 5, 6, 7)), "blade " + blade);
        }
    }

    @Test
    void aCoarseLevelLeavesAnOffsetStateUnshifted() {
        defineBlocks();
        models.offsetLike(GRASS, Blocks.SHORT_GRASS.defaultBlockState());
        Cell cell = blank();
        cell.set(5, 6, 7, block(GRASS));

        CellMesh mesh = mesh(cell, airAround(), COARSE_LEVEL);

        for (int blade = 0; blade < Quad.BLADE_COUNT; blade++) {
            assertEquals(MeshBuffer.UNTINTED, Quad.colourIndex(bladeAt(mesh, blade, 5, 6, 7)), "blade " + blade);
        }
    }

    @Test
    void aPositionalRowTakesEachBlocksOwnModelAndMergesNoTwoDifferentNeighbours() {
        defineVaried((blockX, blockY, blockZ) -> VARIED_MODEL + Math.floorMod(blockX, 2));
        Cell cell = blank();
        for (int x = 0; x < VARIED_ROW; x++) {
            cell.set(x, 0, 0, block(VARIED));
        }

        CellMesh mesh = mesh(cell, airAround(), 0);

        int up = Direction.UP.ordinal();
        assertEquals(VARIED_ROW, mesh.groupCount(up));
        for (int index = mesh.groupStart(up); index < mesh.groupStart(up) + mesh.groupCount(up); index++) {
            long quad = mesh.quad(index);
            assertEquals(1, Quad.width(quad));
            assertEquals(VARIED_MODEL + Quad.x(quad) % 2, Quad.modelId(quad));
        }
    }

    @Test
    void aPositionalStateOfOneModelEverywhereStillMerges() {
        defineVaried((blockX, blockY, blockZ) -> VARIED_MODEL);
        Cell cell = blank();
        for (int x = 0; x < VARIED_ROW; x++) {
            cell.set(x, 0, 0, block(VARIED));
        }

        CellMesh mesh = mesh(cell, airAround(), 0);

        long top = mesh.quad(mesh.groupStart(Direction.UP.ordinal()));
        assertEquals(1, mesh.groupCount(Direction.UP.ordinal()));
        assertEquals(VARIED_ROW, Quad.width(top));
    }

    @Test
    void aCoarseLevelPicksThePositionalModelOfTheVoxelsOriginBlock() {
        defineVaried((blockX, blockY, blockZ) -> blockX == 2 && blockY == MIN_BLOCK_Y + 2 && blockZ == 4
                ? VARIED_MODEL + 1
                : VARIED_MODEL);
        Cell cell = blank();
        cell.set(1, 1, 2, block(VARIED));

        CellMesh mesh = mesh(cell, airAround(), COARSE_LEVEL);

        assertEquals(QuadGroups.DIRECTIONAL_COUNT, mesh.quadCount());
        for (int index = 0; index < mesh.quadCount(); index++) {
            assertEquals(VARIED_MODEL + 1, Quad.modelId(mesh.quad(index)));
        }
    }

    private void defineVaried(FakeModels.PositionalIds ids) {
        defineBlocks();
        int solid = ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, 0, 0);
        models.positional(VARIED, ids);
        models.describe(VARIED_MODEL, solid);
        models.describe(VARIED_MODEL + 1, solid);
        opacities.put(VARIED, StateTable.FULL_OPACITY);
    }

    private static void assertBladesOffset(CellMesh mesh, int x, int y, int z, Vec3 expected) {
        for (int blade = 0; blade < Quad.BLADE_COUNT; blade++) {
            int offset = mesh.offset(bladeAt(mesh, blade, x, y, z));
            assertEquals(expected.x, QuadOffset.x(offset), OFFSET_TOLERANCE, "x of blade " + blade);
            assertEquals(expected.y, QuadOffset.y(offset), OFFSET_TOLERANCE, "y of blade " + blade);
            assertEquals(expected.z, QuadOffset.z(offset), OFFSET_TOLERANCE, "z of blade " + blade);
        }
    }

    private static long bladeAt(CellMesh mesh, int blade, int x, int y, int z) {
        for (int index = 0; index < mesh.quadCount(); index++) {
            long quad = mesh.quad(index);
            if (Quad.face(quad) == Quad.bladeFace(blade) && Quad.x(quad) == x && Quad.y(quad) == y
                    && Quad.z(quad) == z) {
                return quad;
            }
        }

        throw new AssertionError("No blade " + blade + " at " + x + ", " + y + ", " + z);
    }

    private void defineBlocks() {
        int solid = ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, 0, 0);
        int clear = ModelMetadata.pack(FaceMask.ALL, FaceMask.NONE, FaceMask.ALL, 0, 0);
        int glowing = ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, ModelMetadata.MAX_EMISSION, 0);
        int translucent = ModelMetadata.pack(
                FaceMask.ALL, FaceMask.NONE, FaceMask.ALL, 0, ModelMetadata.TRANSLUCENT);

        models.define(STONE, STONE_MODEL, solid);
        models.define(GLASS, GLASS_MODEL, clear);
        models.define(LAVA, LAVA_MODEL, glowing);
        models.define(TINT_A, TINT_A_MODEL, translucent);
        models.define(TINT_B, TINT_B_MODEL, translucent);
        models.define(WATER, WATER_MODEL, translucent);
        models.define(FLOWING_WATER, FLOWING_WATER_MODEL, translucent);
        models.define(WATERLOGGED, WATERLOGGED_MODEL, clear);
        models.define(WET_LEAVES, WET_LEAVES_MODEL, solid);
        models.define(GRASS, GRASS_MODEL, ModelMetadata.pack(
                FaceMask.NONE, FaceMask.NONE, FaceMask.NONE, 0, ModelMetadata.BLADED));
        models.define(HEADED, HEADED_MODEL, ModelMetadata.pack(FaceMask.EAST | FaceMask.WEST, FaceMask.NONE,
                FaceMask.NONE, 0, ModelMetadata.BLADED | ModelMetadata.SLOPED));
        models.define(OPAQUE_LEAVES, OPAQUE_LEAVES_MODEL, solid);
        models.define(CUTOUT_LEAVES, CUTOUT_LEAVES_MODEL, clear);
        models.define(GRASS_BLOCK, GRASS_BLOCK_MODEL, solid);
        models.tint(GRASS_BLOCK_MODEL, GRASS_ROW);
        models.defineFluid(WATERLOGGED, WATER_MODEL, translucent);
        models.defineFluid(WET_LEAVES, WATER_MODEL, translucent);
        models.submerge(WATER_MODEL, SUBMERGED_WATER_MODEL);
        models.submerge(FLOWING_WATER_MODEL, SUBMERGED_WATER_MODEL);

        opacities.put(STONE, StateTable.FULL_OPACITY);
        opacities.put(LAVA, StateTable.FULL_OPACITY);
        opacities.put(WET_LEAVES, StateTable.FULL_OPACITY);
        opacities.put(OPAQUE_LEAVES, StateTable.FULL_OPACITY);
        opacities.put(CUTOUT_LEAVES, StateTable.FULL_OPACITY);
        opacities.put(GRASS_BLOCK, StateTable.FULL_OPACITY);
    }

    private void defineLava() {
        defineBlocks();
        int surface = ModelMetadata.pack(FaceMask.ALL, FaceMask.DOWN, FaceMask.ALL & ~FaceMask.UP,
                ModelMetadata.MAX_EMISSION, ModelMetadata.FLUID);
        int submerged = ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, ModelMetadata.MAX_EMISSION,
                ModelMetadata.FLUID);

        models.define(SOURCE_LAVA, SOURCE_LAVA_MODEL, surface);
        models.define(FLOWING_LAVA, FLOWING_LAVA_MODEL, surface);
        models.submerge(SOURCE_LAVA_MODEL, SUBMERGED_LAVA_MODEL);
        models.submerge(FLOWING_LAVA_MODEL, SUBMERGED_LAVA_MODEL);
        models.describe(SUBMERGED_LAVA_MODEL, submerged);

        opacities.put(SOURCE_LAVA, SEE_THROUGH_DAMPENING);
        opacities.put(FLOWING_LAVA, SEE_THROUGH_DAMPENING);
        models.holds(SOURCE_LAVA, LAVA_FLUID, SOURCE_HEIGHT);
        models.holds(FLOWING_LAVA, LAVA_FLUID, FLOWING_HEIGHT);
        models.makeSolid(STONE);
    }

    private static int cornersOf(CellMesh mesh, Direction face, int x, int y, int z) {
        for (int index = 0; index < mesh.quadCount(); index++) {
            long quad = mesh.quad(index);
            if (Quad.face(quad) == face.ordinal() && Quad.x(quad) == x && Quad.y(quad) == y && Quad.z(quad) == z) {
                return mesh.offset(quad);
            }
        }

        throw new AssertionError("No " + face + " quad at " + x + ", " + y + ", " + z);
    }

    private CellMesh mesh(Cell centre, Map<Direction, Cell> around, ColumnCoverage coverage) {
        long key = CellKey.pack(0, 0, 0, 0);
        MeshScratch scratch = new MeshScratch();
        scratch.voxels().load(centre);
        around.forEach((face, cell) -> scratch.voxels().loadNeighbour(face, cell));
        scratch.voxels().loadCoverage(coverage, key);
        scratch.blend().begin(scratch.voxels(), tints, key, NO_BLEND);

        return new CellMesher(scratch, models, FRAME)
                .mesh(key, centre.occupancy(), opacity, () -> bakeRequests++);
    }

    private static ColumnCoverage coverage(long... chunks) {
        ColumnCoverage coverage = new ColumnCoverage(chunk -> { });
        for (long chunk : chunks) {
            coverage.load(chunk);
        }

        return coverage;
    }

    private CellMesh mesh(Cell centre, Map<Direction, Cell> around, int level) {
        MeshScratch scratch = new MeshScratch();
        scratch.voxels().load(centre);
        around.forEach((face, cell) -> scratch.voxels().loadNeighbour(face, cell));
        long key = CellKey.pack(level, 0, 0, 0);
        scratch.blend().begin(scratch.voxels(), tints, key, NO_BLEND);

        return new CellMesher(scratch, models, FRAME)
                .mesh(key, centre.occupancy(), opacity, () -> bakeRequests++);
    }

    private static Cell blank() {
        return Cell.blank(CellKey.pack(0, 0, 0, 0));
    }

    private static long block(int stateId) {
        return VoxelEntry.pack(stateId, BIOME, VoxelEntry.light(FULL_SKY, NO_BLOCK_LIGHT));
    }

    private static Cell cube(int stateId) {
        Cell cell = blank();
        for (int y = 0; y < CUBE_SIDE; y++) {
            for (int z = 0; z < CUBE_SIDE; z++) {
                for (int x = 0; x < CUBE_SIDE; x++) {
                    cell.set(x, y, z, block(stateId));
                }
            }
        }

        return cell;
    }

    private static Cell floor() {
        Cell cell = blank();
        for (int z = 0; z < SIDE; z++) {
            for (int x = 0; x < SIDE; x++) {
                cell.set(x, 0, z, block(STONE));
            }
        }

        return cell;
    }

    private static Cell litFloor() {
        Cell cell = floor();
        for (int z = 0; z < SIDE; z++) {
            for (int x = 0; x < SIDE; x++) {
                int sky = GRADIENT_BASE + x % GRADIENT_SPAN;
                cell.set(x, 1, z, VoxelEntry.pack(AIR, BIOME, VoxelEntry.light(sky, NO_BLOCK_LIGHT)));
            }
        }

        return cell;
    }

    private static Map<Direction, Cell> airAround() {
        Map<Direction, Cell> around = new HashMap<>();
        for (Direction face : Direction.values()) {
            around.put(face, blank());
        }

        return around;
    }

    private static Map<Direction, Cell> ground() {
        Map<Direction, Cell> around = airAround();
        Cell below = blank();
        for (int z = 0; z < SIDE; z++) {
            for (int x = 0; x < SIDE; x++) {
                below.set(x, LAST, z, block(STONE));
            }
        }

        around.put(Direction.DOWN, below);
        around.put(Direction.WEST, sideFloor(LAST, true));
        around.put(Direction.EAST, sideFloor(0, true));
        around.put(Direction.NORTH, sideFloor(LAST, false));
        around.put(Direction.SOUTH, sideFloor(0, false));
        return around;
    }

    private static Cell sideFloor(int at, boolean alongX) {
        Cell cell = blank();
        for (int other = 0; other < SIDE; other++) {
            if (alongX) {
                cell.set(at, 0, other, block(STONE));
            } else {
                cell.set(other, 0, at, block(STONE));
            }
        }

        return cell;
    }

    private static boolean hasBlade(CellMesh mesh, int blade, int x, int y, int z, int modelId) {
        for (int index = 0; index < mesh.quadCount(); index++) {
            long quad = mesh.quad(index);
            if (Quad.face(quad) == Quad.bladeFace(blade) && Quad.x(quad) == x && Quad.y(quad) == y
                    && Quad.z(quad) == z && Quad.modelId(quad) == modelId) {
                return true;
            }
        }

        return false;
    }

    private static boolean has(CellMesh mesh, Direction face, int x, int y, int z, int modelId) {
        for (int index = 0; index < mesh.quadCount(); index++) {
            long quad = mesh.quad(index);
            if (Quad.face(quad) == face.ordinal() && Quad.x(quad) == x && Quad.y(quad) == y
                    && Quad.z(quad) == z && Quad.modelId(quad) == modelId) {
                return true;
            }
        }

        return false;
    }

    private static int lightAt(CellMesh mesh, Direction face, int x, int y, int z, int modelId) {
        for (int index = 0; index < mesh.quadCount(); index++) {
            long quad = mesh.quad(index);
            if (Quad.face(quad) == face.ordinal() && Quad.x(quad) == x && Quad.y(quad) == y
                    && Quad.z(quad) == z && Quad.modelId(quad) == modelId) {
                return Quad.light(quad);
            }
        }

        return NO_QUAD;
    }

    private static boolean absent(CellMesh mesh, Direction face, int x, int y, int z) {
        for (int index = 0; index < mesh.quadCount(); index++) {
            long quad = mesh.quad(index);
            if (Quad.face(quad) == face.ordinal() && Quad.x(quad) == x && Quad.y(quad) == y
                    && Quad.z(quad) == z) {
                return false;
            }
        }

        return true;
    }
}
