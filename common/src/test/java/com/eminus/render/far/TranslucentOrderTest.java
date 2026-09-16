package com.eminus.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.QuadGroups;
import com.eminus.render.tree.RenderList;

import org.junit.jupiter.api.Test;

class TranslucentOrderTest {
    private static final int LEVEL = 0;
    private static final int ONE_QUAD = 1;
    private static final double CAMERA_X = 16.0;
    private static final double CAMERA_Y = 16.0;
    private static final double CAMERA_Z = 16.0;
    private static final double NEXT_CELL_X = 48.0;

    private final CellFrame frame = new CellFrame(0);
    private final TranslucentOrder order = new TranslucentOrder();
    private final CellMesh near = translucent(CellKey.pack(LEVEL, 0, 0, 0));
    private final CellMesh middle = translucent(CellKey.pack(LEVEL, 2, 0, 0));
    private final CellMesh far = translucent(CellKey.pack(LEVEL, 5, 0, 0));

    @Test
    void farCellsAreOrderedBeforeNearOnes() {
        update(list(near, far, middle));

        assertEquals(List.of(far, middle, near), order.meshes());
        assertEquals(1, order.takeSorts());
    }

    @Test
    void aCellWithoutTranslucentQuadsIsLeftOut() {
        CellMesh opaque = opaque(CellKey.pack(LEVEL, 1, 0, 0));

        update(list(near, opaque, far));

        assertEquals(List.of(far, near), order.meshes());
        assertEquals(2, order.meshes().size());
    }

    @Test
    void aCoarseCellSortsByItsOwnCentre() {
        CellMesh coarse = translucent(CellKey.pack(2, 1, 0, 0));

        update(list(coarse, far));

        assertEquals(List.of(coarse, far), order.meshes());
    }

    @Test
    void aStillCameraSortsOnlyOnce() {
        RenderList walked = list(near, far);

        update(walked);
        assertEquals(1, order.takeSorts());

        update(walked);
        assertEquals(0, order.takeSorts());
    }

    @Test
    void crossingACellResorts() {
        RenderList walked = list(near, far);

        update(walked);
        order.takeSorts();

        order.update(walked, frame, NEXT_CELL_X, CAMERA_Y, CAMERA_Z);

        assertEquals(1, order.takeSorts());
    }

    @Test
    void movingInsideOneCellDoesNotResort() {
        RenderList walked = list(near, far);

        update(walked);
        order.takeSorts();

        order.update(walked, frame, CAMERA_X + 1.0, CAMERA_Y, CAMERA_Z);

        assertEquals(0, order.takeSorts());
    }

    @Test
    void aRenderListWithNewMembersResorts() {
        update(list(near, far));
        order.takeSorts();

        update(list(near, far, middle));

        assertEquals(1, order.takeSorts());
        assertEquals(List.of(far, middle, near), order.meshes());
    }

    @Test
    void aNewRenderListOverTheSameMembersDoesNotResort() {
        update(list(near, far));
        order.takeSorts();

        update(list(near, far));

        assertEquals(0, order.takeSorts());
    }

    private void update(RenderList walked) {
        order.update(walked, frame, CAMERA_X, CAMERA_Y, CAMERA_Z);
    }

    private static RenderList list(CellMesh... meshes) {
        return new RenderList(List.of(meshes));
    }

    private static CellMesh translucent(long key) {
        return mesh(key, QuadGroups.TRANSLUCENT);
    }

    private static CellMesh opaque(long key) {
        return mesh(key, 0);
    }

    private static CellMesh mesh(long key, int group) {
        int[] counts = new int[QuadGroups.COUNT];
        counts[group] = ONE_QUAD;
        return new CellMesh(key, 0, new long[ONE_QUAD], new int[QuadGroups.COUNT], counts, new int[0]);
    }
}
