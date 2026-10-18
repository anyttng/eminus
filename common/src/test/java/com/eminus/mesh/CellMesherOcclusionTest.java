package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.Cell;
import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.FaceMask;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.VoxelEntry;
import com.eminus.model.ModelMetadata;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CellMesherOcclusionTest {
    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int CENTER = SIDE / 2;
    private static final int STONE = 1;
    private static final int STONE_MODEL = 10;
    private static final int BIOME = 3;
    private static final int FULL_SKY = 15;
    private static final int NO_BLOCK_LIGHT = 0;
    private static final int NO_BLEND = 0;
    private static final int COARSE_LEVEL = 1;
    private static final int MIN_BLOCK_Y = -64;
    private static final CellFrame FRAME = new CellFrame(MIN_BLOCK_Y);
    private static final StateOpacity OPACITY = new StateOpacity() {
        @Override
        public int opacity(int stateId) {
            return 0;
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
    void aFloorBesideAPillarCarriesWordsOnEveryLevel() {
        for (int level = DetailLevel.MIN; level <= COARSE_LEVEL; level++) {
            CellMesh mesh = mesh(floorWithPillar(level), level, MeshOcclusion.EVERY_LEVEL);

            assertTrue(mesh.occlusion().length > 0, "level " + level);
            assertTrue(anyWord(mesh), "level " + level);
        }
    }

    @Test
    void theFinestLevelOnlyKeepsLevelZeroWordsAndDropsTheCoarserOnes() {
        CellMesh finest = mesh(floorWithPillar(DetailLevel.MIN), DetailLevel.MIN, MeshOcclusion.FINEST_LEVEL);
        CellMesh coarse = mesh(floorWithPillar(COARSE_LEVEL), COARSE_LEVEL, MeshOcclusion.FINEST_LEVEL);

        assertTrue(anyWord(finest));
        assertEquals(0, coarse.occlusion().length);
        assertFalse(anyWord(coarse));
    }

    @Test
    void offLeavesNoMeshAWordOrAStream() {
        for (int level = DetailLevel.MIN; level <= COARSE_LEVEL; level++) {
            CellMesh mesh = mesh(floorWithPillar(level), level, MeshOcclusion.NONE);

            assertEquals(0, mesh.occlusion().length, "level " + level);
            assertFalse(anyWord(mesh), "level " + level);
        }
    }

    @Test
    void withoutWordsTheFloorMergesAcrossTheEdgesTheWordsWouldDarken() {
        CellMesh occluded = mesh(floorWithPillar(DetailLevel.MIN), DetailLevel.MIN, MeshOcclusion.EVERY_LEVEL);
        CellMesh plain = mesh(floorWithPillar(DetailLevel.MIN), DetailLevel.MIN, MeshOcclusion.NONE);

        assertTrue(plain.quadCount() < occluded.quadCount(),
                plain.quadCount() + " quads without words, " + occluded.quadCount() + " with them");
    }

    @Test
    void aCubeWithNothingDarkAroundItCarriesNoStream() {
        defineStone();
        Cell cell = Cell.blank(CellKey.pack(DetailLevel.MIN, 0, 0, 0));
        cell.set(CENTER, CENTER, CENTER, block());

        CellMesh mesh = mesh(cell, DetailLevel.MIN, MeshOcclusion.EVERY_LEVEL);

        assertEquals(QuadGroups.DIRECTIONAL_COUNT, mesh.quadCount());
        assertEquals(0, mesh.occlusion().length);
    }

    private Cell floorWithPillar(int level) {
        defineStone();
        Cell cell = Cell.blank(CellKey.pack(level, 0, 0, 0));
        for (int z = 0; z < SIDE; z++) {
            for (int x = 0; x < SIDE; x++) {
                cell.set(x, 0, z, block());
            }
        }

        cell.set(CENTER, 1, CENTER, block());
        return cell;
    }

    private void defineStone() {
        models.define(STONE, STONE_MODEL,
                ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, 0, ModelMetadata.SMOOTH));
        models.shadeAs(STONE, StateShade.DARK | StateShade.FULL_COLLISION);
    }

    private CellMesh mesh(Cell cell, int level, MeshOcclusion occlusion) {
        MeshScratch scratch = new MeshScratch();
        scratch.voxels().load(cell);
        for (Direction face : Direction.values()) {
            scratch.voxels().loadRegion(face.getStepX(), face.getStepY(), face.getStepZ(),
                    Cell.blank(CellKey.pack(level, 0, 0, 0)));
        }

        long key = CellKey.pack(level, 0, 0, 0);
        scratch.begin(FRAME, key, tints, NO_BLEND);
        CellMesh mesh = new CellMesher(scratch, models).mesh(key, cell.occupancy(), OPACITY, occlusion.at(level),
                () -> { });
        assertNotNull(mesh);
        return mesh;
    }

    private static boolean anyWord(CellMesh mesh) {
        for (int index = 0; index < mesh.quadCount(); index++) {
            if (mesh.occlusion(index) != OcclusionWord.NONE) {
                return true;
            }
        }

        return false;
    }

    private static long block() {
        return VoxelEntry.pack(STONE, BIOME, VoxelEntry.light(FULL_SKY, NO_BLOCK_LIGHT));
    }
}
