package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.Cell;
import com.eminus.cell.CellFrame;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.FaceMask;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.StateTable;
import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CellMesherLightTest {
    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int CENTER = SIDE / 2;
    private static final int STONE = 1;
    private static final int VINE = 2;
    private static final int RAISED = 3;
    private static final int WATER = 4;
    private static final int STONE_MODEL = 10;
    private static final int VINE_MODEL = 11;
    private static final int RAISED_MODEL = 12;
    private static final int WATER_MODEL = 13;
    private static final int SUBMERGED_WATER_MODEL = 14;
    private static final int WATER_FLUID = 1;
    private static final float SOURCE_HEIGHT = 8.0F / 9.0F;
    private static final float VINE_OFFSET = 0.8F / 16.0F;
    private static final float RAISED_INSET = 1.0F / 16.0F;
    private static final int BIOME = 3;
    private static final int FULL_SKY = 15;
    private static final int NO_LIGHT = VoxelEntry.light(0, 0);
    private static final int OWN_LIGHT = VoxelEntry.light(12, 5);
    private static final int ABOVE_LIGHT = VoxelEntry.light(FULL_SKY, 14);
    private static final int DIM_ABOVE_LIGHT = VoxelEntry.light(4, 14);
    private static final int BELOW_LIGHT = VoxelEntry.light(13, 0);
    private static final int BESIDE_LIGHT = VoxelEntry.light(9, 2);
    private static final int LAST_IN_FIRST_CHUNK = 15;
    private static final long FIRST_CHUNK = ColumnCoverage.pack(0, 0);
    private static final int NO_BLEND = 0;
    private static final int COARSE_LEVEL = 1;
    private static final int NO_QUAD = -1;
    private static final int MIN_BLOCK_Y = -64;
    private static final CellFrame FRAME = new CellFrame(MIN_BLOCK_Y);
    private static final StateOpacity OPACITY = new StateOpacity() {
        @Override
        public int opacity(int stateId) {
            return stateId == STONE ? StateTable.FULL_OPACITY : 0;
        }

        @Override
        public boolean cover(int stateId) {
            return false;
        }
    };

    private final FakeModels models = new FakeModels();
    private final FakeTints tints = new FakeTints();

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    @Test
    void bothFacesOfAVineAgainstStoneCarryTheVinesOwnLightOnEveryLevel() {
        defineBlocks();
        for (int level = DetailLevel.MIN; level <= COARSE_LEVEL; level++) {
            Cell cell = blank(level);
            cell.set(CENTER - 1, CENTER, CENTER, VoxelEntry.pack(STONE, BIOME, NO_LIGHT));
            cell.set(CENTER, CENTER, CENTER, VoxelEntry.pack(VINE, BIOME, OWN_LIGHT));

            CellMesh mesh = mesh(cell, level);

            assertEquals(OWN_LIGHT, lightAt(mesh, Direction.WEST, VINE_MODEL), "level " + level);
            assertEquals(OWN_LIGHT, lightAt(mesh, Direction.EAST, VINE_MODEL), "level " + level);
        }
    }

    @Test
    void anInsetTopOfAFullCollisionStateKeepsTheLightAboveOnEveryLevel() {
        defineBlocks();
        for (int level = DetailLevel.MIN; level <= COARSE_LEVEL; level++) {
            Cell cell = blank(level);
            cell.set(CENTER, CENTER, CENTER, VoxelEntry.pack(RAISED, BIOME, OWN_LIGHT));
            cell.set(CENTER, CENTER + 1, CENTER, VoxelEntry.pack(VoxelEntry.AIR_STATE_ID, BIOME, ABOVE_LIGHT));

            CellMesh mesh = mesh(cell, level);

            assertEquals(ABOVE_LIGHT, lightAt(mesh, Direction.UP, RAISED_MODEL), "level " + level);
        }
    }

    @Test
    void aFluidTopUnderStoneKeepsTheFluidsOwnLightOnEveryLevel() {
        defineBlocks();
        for (int level = DetailLevel.MIN; level <= COARSE_LEVEL; level++) {
            Cell cell = blank(level);
            cell.set(CENTER, CENTER, CENTER, VoxelEntry.pack(WATER, BIOME, OWN_LIGHT));
            cell.set(CENTER, CENTER + 1, CENTER, VoxelEntry.pack(STONE, BIOME, NO_LIGHT));

            CellMesh mesh = mesh(cell, level);

            assertEquals(OWN_LIGHT, lightAt(mesh, Direction.UP, WATER_MODEL), "level " + level);
        }
    }

    @Test
    void aFluidFaceTakesTheBrighterOfEachChannelAsTheGamesFluidLightDoesOnEveryLevel() {
        defineBlocks();
        for (int level = DetailLevel.MIN; level <= COARSE_LEVEL; level++) {
            Cell cell = blank(level);
            cell.set(CENTER, CENTER, CENTER, VoxelEntry.pack(WATER, BIOME, OWN_LIGHT));
            cell.set(CENTER, CENTER + 1, CENTER, VoxelEntry.pack(VoxelEntry.AIR_STATE_ID, BIOME, DIM_ABOVE_LIGHT));
            cell.set(CENTER, CENTER - 1, CENTER, VoxelEntry.pack(VoxelEntry.AIR_STATE_ID, BIOME, BELOW_LIGHT));
            cell.set(CENTER + 1, CENTER, CENTER, VoxelEntry.pack(VoxelEntry.AIR_STATE_ID, BIOME, BESIDE_LIGHT));

            CellMesh mesh = mesh(cell, level);

            assertEquals(VoxelEntry.light(12, 14), lightAt(mesh, Direction.UP, WATER_MODEL), "level " + level);
            assertEquals(VoxelEntry.light(12, 14), lightAt(mesh, Direction.EAST, WATER_MODEL), "level " + level);
            assertEquals(VoxelEntry.light(13, 5), lightAt(mesh, Direction.DOWN, WATER_MODEL), "level " + level);
        }
    }

    @Test
    void aFluidFaceOnTheCutKeepsTheLightOfTheColumnNeverIngested() {
        defineBlocks();
        Cell cell = blank(DetailLevel.MIN);
        cell.set(LAST_IN_FIRST_CHUNK, CENTER, CENTER, VoxelEntry.pack(WATER, BIOME, NO_LIGHT));
        cell.set(LAST_IN_FIRST_CHUNK, CENTER + 1, CENTER, VoxelEntry.pack(STONE, BIOME, NO_LIGHT));
        ColumnCoverage coverage = new ColumnCoverage(DetailLevel.MIN, (chunk, lowestLevel) -> { });
        coverage.load(FIRST_CHUNK, DetailLevel.MIN);

        CellMesh mesh = mesh(cell, DetailLevel.MIN, coverage);

        assertEquals(VoxelEntry.light(FULL_SKY, 0),
                lightAt(mesh, Direction.EAST, models.oneSidedModelId(WATER_MODEL)));
    }

    private void defineBlocks() {
        models.define(STONE, STONE_MODEL, ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, 0, 0));
        models.define(VINE, VINE_MODEL,
                ModelMetadata.pack(FaceMask.EAST | FaceMask.WEST, FaceMask.NONE, FaceMask.NONE, 0, 0));
        models.inset(VINE_MODEL, Direction.WEST, VINE_OFFSET);
        models.inset(VINE_MODEL, Direction.EAST, 1.0F - VINE_OFFSET);

        int sides = FaceMask.ALL & ~FaceMask.UP;
        models.define(RAISED, RAISED_MODEL, ModelMetadata.pack(FaceMask.ALL, sides, sides, 0, 0));
        models.inset(RAISED_MODEL, Direction.UP, RAISED_INSET);
        models.shadeAs(RAISED, StateShade.FULL_COLLISION);

        models.define(WATER, WATER_MODEL, ModelMetadata.pack(FaceMask.ALL, FaceMask.NONE, FaceMask.ALL & ~FaceMask.UP,
                0, ModelMetadata.FLUID | ModelMetadata.TRANSLUCENT));
        models.submerge(WATER_MODEL, SUBMERGED_WATER_MODEL);
        models.describe(SUBMERGED_WATER_MODEL, ModelMetadata.pack(FaceMask.ALL, FaceMask.NONE, FaceMask.ALL, 0,
                ModelMetadata.FLUID | ModelMetadata.TRANSLUCENT));
        models.partialHeight(WATER_MODEL);
        models.holds(WATER, WATER_FLUID, SOURCE_HEIGHT);
        models.makeSolid(STONE);
    }

    private CellMesh mesh(Cell cell, int level) {
        return mesh(cell, level, null);
    }

    private CellMesh mesh(Cell cell, int level, @Nullable ColumnCoverage coverage) {
        MeshScratch scratch = new MeshScratch();
        scratch.voxels().load(cell);
        for (Direction face : Direction.values()) {
            scratch.voxels().loadRegion(face.getStepX(), face.getStepY(), face.getStepZ(), blank(level));
        }

        long key = CellKey.pack(level, 0, 0, 0);
        if (coverage != null) {
            scratch.voxels().loadCoverage(coverage, key);
        }

        scratch.begin(FRAME, key, tints, NO_BLEND);
        CellMesh mesh = new CellMesher(scratch, models).mesh(key, cell.occupancy(), OPACITY, true, () -> { });
        assertNotNull(mesh);
        return mesh;
    }

    private static Cell blank(int level) {
        return Cell.blank(CellKey.pack(level, 0, 0, 0));
    }

    private static int lightAt(CellMesh mesh, Direction face, int modelId) {
        for (int index = 0; index < mesh.quadCount(); index++) {
            long quad = mesh.quad(index);
            if (Quad.face(quad) == face.get3DDataValue() && Quad.modelId(quad) == modelId) {
                return Quad.light(quad);
            }
        }

        return NO_QUAD;
    }
}
