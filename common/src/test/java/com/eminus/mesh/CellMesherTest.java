package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.FaceMask;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.StateTable;
import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

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

    private static final int STONE_MODEL = 10;
    private static final int GLASS_MODEL = 11;
    private static final int LAVA_MODEL = 12;
    private static final int TINT_A_MODEL = 13;
    private static final int TINT_B_MODEL = 14;
    private static final int WATER_MODEL = 15;
    private static final int WATERLOGGED_MODEL = 16;
    private static final int WET_LEAVES_MODEL = 17;

    private static final int BIOME = 3;
    private static final int FULL_SKY = 15;
    private static final int NO_BLOCK_LIGHT = 0;
    private static final int GRADIENT_BASE = 12;
    private static final int GRADIENT_SPAN = 4;
    private static final int GLASS_FACES_IN_AIR = 5;
    private static final int CUBE_SIDE = 2;
    private static final int CUBE_FACES = 6;
    private static final int NEIGHBOUR_X = 2;
    private static final int WATERLOGGED_FACES = QuadGroups.FACE_COUNT * 2;

    private final Map<Integer, Integer> opacities = new HashMap<>();
    private final FakeModels models = new FakeModels();
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

        assertEquals(QuadGroups.FACE_COUNT, mesh.quadCount());
        for (int group = 0; group < QuadGroups.FACE_COUNT; group++) {
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
        assertEquals(QuadGroups.FACE_COUNT + GLASS_FACES_IN_AIR, mesh.quadCount());
    }

    @Test
    void lavaCarriesItsEmissionThroughADarkNeighbour() {
        defineBlocks();
        Cell cell = blank();
        cell.set(4, 4, 4, block(LAVA));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertEquals(QuadGroups.FACE_COUNT, mesh.quadCount());
        for (int index = 0; index < mesh.quadCount(); index++) {
            assertEquals(VoxelEntry.light(FULL_SKY, ModelMetadata.MAX_EMISSION), Quad.light(mesh.quad(index)));
        }
    }

    @Test
    void aLightGradientSplitsTheFloorAtLevelZeroAndMergesItAtLevelTwo() {
        defineBlocks();
        CellMesh fine = mesh(litFloor(), ground(), 0);
        CellMesh coarse = mesh(litFloor(), ground(), 2);

        assertEquals(SIDE * 2, fine.groupCount(Direction.UP.ordinal()));
        assertEquals(4, coarse.groupCount(Direction.UP.ordinal()));
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
        assertEquals(QuadGroups.FACE_COUNT, mesh.groupCount(QuadGroups.TRANSLUCENT));
        assertTrue(has(mesh, Direction.EAST, 5, 5, 5, WATERLOGGED_MODEL));
        assertTrue(has(mesh, Direction.EAST, 5, 5, 5, WATER_MODEL));
    }

    @Test
    void aPureWaterBlockAsksForNoSecondModel() {
        defineBlocks();
        Cell cell = blank();
        cell.set(5, 5, 5, block(WATER));

        CellMesh mesh = mesh(cell, airAround(), 0);

        assertEquals(QuadGroups.FACE_COUNT, mesh.quadCount());
        assertEquals(QuadGroups.FACE_COUNT, mesh.groupCount(QuadGroups.TRANSLUCENT));
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
        assertEquals(QuadGroups.FACE_COUNT, mesh.groupCount(QuadGroups.TRANSLUCENT));
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
        models.define(WATERLOGGED, WATERLOGGED_MODEL, clear);
        models.define(WET_LEAVES, WET_LEAVES_MODEL, solid);
        models.defineFluid(WATERLOGGED, WATER_MODEL, translucent);
        models.defineFluid(WET_LEAVES, WATER_MODEL, translucent);

        opacities.put(STONE, StateTable.FULL_OPACITY);
        opacities.put(LAVA, StateTable.FULL_OPACITY);
        opacities.put(WET_LEAVES, StateTable.FULL_OPACITY);
    }

    private CellMesh mesh(Cell centre, Map<Direction, Cell> around, int level) {
        MeshScratch scratch = new MeshScratch();
        scratch.voxels().load(centre);
        around.forEach((face, cell) -> scratch.voxels().loadNeighbour(face, cell));

        return new CellMesher(scratch, models)
                .mesh(CellKey.pack(level, 0, 0, 0), centre.occupancy(), opacity, () -> bakeRequests++);
    }

    private static Cell blank() {
        return Cell.blank(CellKey.pack(0, 0, 0, 0));
    }

    private static long block(int stateId) {
        return VoxelEntry.pack(stateId, BIOME, VoxelEntry.light(FULL_SKY, NO_BLOCK_LIGHT));
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
