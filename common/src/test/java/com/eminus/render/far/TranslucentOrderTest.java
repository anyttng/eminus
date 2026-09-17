package com.eminus.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.mesh.MeshSummary;
import com.eminus.render.tree.RenderList;

import org.junit.jupiter.api.Test;

class TranslucentOrderTest {
    private static final int LEVEL = 0;
    private static final int ONE_QUAD = 1;
    private static final int NO_QUADS = 0;
    private static final int NO_OCCUPANCY = 0;
    private static final double CAMERA_X = 16.0;
    private static final double CAMERA_Y = 16.0;
    private static final double CAMERA_Z = 16.0;
    private static final double NEXT_CELL_X = 48.0;

    private final CellFrame frame = new CellFrame(0);
    private final TranslucentOrder order = new TranslucentOrder();
    private final MeshSummary near = translucent(CellKey.pack(LEVEL, 0, 0, 0));
    private final MeshSummary middle = translucent(CellKey.pack(LEVEL, 2, 0, 0));
    private final MeshSummary far = translucent(CellKey.pack(LEVEL, 5, 0, 0));

    @Test
    void farCellsAreOrderedBeforeNearOnes() {
        update(list(near, far, middle));

        assertEquals(List.of(far, middle, near), order.meshes());
        assertEquals(1, order.takeSorts());
    }

    @Test
    void aCellWithoutTranslucentQuadsIsLeftOut() {
        MeshSummary opaque = opaque(CellKey.pack(LEVEL, 1, 0, 0));

        update(list(near, opaque, far));

        assertEquals(List.of(far, near), order.meshes());
        assertEquals(2, order.meshes().size());
    }

    @Test
    void aCoarseCellSortsByItsOwnCentre() {
        MeshSummary coarse = translucent(CellKey.pack(2, 1, 0, 0));

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

    private static RenderList list(MeshSummary... meshes) {
        return new RenderList(List.of(meshes));
    }

    private static MeshSummary translucent(long key) {
        return new MeshSummary(key, NO_OCCUPANCY, ONE_QUAD, ONE_QUAD);
    }

    private static MeshSummary opaque(long key) {
        return new MeshSummary(key, NO_OCCUPANCY, ONE_QUAD, NO_QUADS);
    }
}
