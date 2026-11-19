package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

class CellMesherBladesTest {
    private static final int SIDE = DetailLevel.VOXELS_PER_SIDE;
    private static final int ROW_Y = 4;
    private static final int ROW_Z = SIDE / 2;
    private static final int GRASS = 9;
    private static final int GRASS_MODEL = 18;
    private static final int BIOME = 3;
    private static final int FULL_SKY = 15;
    private static final int NO_BLOCK_LIGHT = 0;
    private static final int NO_BLEND = 0;
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
    void bothEmitsTwoBladesOnEveryBladedVoxel() {
        CellMesh mesh = mesh(CellKey.pack(DetailLevel.MIN, 0, 0, 0), MeshBlades.BOTH);

        assertEquals(SIDE * Quad.BLADE_COUNT, mesh.quadCount());
    }

    @Test
    void oneEmitsASingleBladeOnEveryBladedVoxelAndUsesBothDiagonals() {
        CellMesh mesh = mesh(CellKey.pack(DetailLevel.MIN, 0, 0, 0), MeshBlades.ONE);

        assertEquals(SIDE, mesh.quadCount());
        int first = 0;
        for (int index = 0; index < mesh.quadCount(); index++) {
            long quad = mesh.quad(index);
            assertTrue(Quad.isBlade(quad));
            first += Quad.face(quad) == Quad.bladeFace(0) ? 1 : 0;
        }

        assertTrue(first > 0 && first < SIDE, "voxels on the first diagonal: " + first);
    }

    @Test
    void oneFollowsTheVoxelsPlaceInTheWorldNotInItsCell() {
        String here = diagonals(mesh(CellKey.pack(DetailLevel.MIN, 0, 0, 0), MeshBlades.ONE));
        String east = diagonals(mesh(CellKey.pack(DetailLevel.MIN, 1, 0, 0), MeshBlades.ONE));

        assertNotEquals(here, east);
    }

    private CellMesh mesh(long key, MeshBlades blades) {
        models.define(GRASS, GRASS_MODEL,
                ModelMetadata.pack(FaceMask.NONE, FaceMask.NONE, FaceMask.NONE, 0, ModelMetadata.BLADED));
        Cell cell = Cell.blank(key);
        for (int x = 0; x < SIDE; x++) {
            cell.set(x, ROW_Y, ROW_Z, VoxelEntry.pack(GRASS, BIOME, VoxelEntry.light(FULL_SKY, NO_BLOCK_LIGHT)));
        }

        MeshScratch scratch = new MeshScratch();
        scratch.voxels().load(cell);
        for (Direction face : Direction.values()) {
            scratch.voxels().loadRegion(face.getStepX(), face.getStepY(), face.getStepZ(), Cell.blank(key));
        }

        scratch.begin(FRAME, key, tints, NO_BLEND);
        CellMesh mesh = new CellMesher(scratch, models, QuadLight.UNDER_SKY).mesh(key, cell.occupancy(), OPACITY,
                MeshOcclusion.EVERY_LEVEL, blades, () -> { });
        assertNotNull(mesh);
        return mesh;
    }

    private static String diagonals(CellMesh mesh) {
        char[] row = new char[SIDE];
        for (int index = 0; index < mesh.quadCount(); index++) {
            long quad = mesh.quad(index);
            row[Quad.x(quad)] = Quad.face(quad) == Quad.bladeFace(0) ? '0' : '1';
        }

        return new String(row);
    }
}
