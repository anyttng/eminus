package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;
import com.eminus.mesh.CellMesh;

import org.junit.jupiter.api.Test;

class TreeBatchTest {
    private static final long FIRST = CellKey.pack(DetailLevel.MAX, 0, 0, 0);
    private static final long SECOND = CellKey.pack(DetailLevel.MAX, 1, 0, 0);
    private static final int ONE_KEY = 1;
    private static final long OLDER_REQUEST = 1L;
    private static final long OTHER_REQUEST = 2L;
    private static final long NEWER_REQUEST = 3L;

    private final TreeBatch batch = new TreeBatch();

    @Test
    void aNewerMeshOfAKeyReplacesTheOlderInPlaceWithItsRequest() {
        CellMesh older = TestMeshes.of(FIRST, OccupancyMask.EMPTY);
        CellMesh other = TestMeshes.of(SECOND, OccupancyMask.EMPTY);
        CellMesh newer = TestMeshes.of(FIRST, OccupancyMask.EMPTY);

        batch.add(older, OLDER_REQUEST);
        batch.add(other, OTHER_REQUEST);
        batch.add(newer, NEWER_REQUEST);

        assertEquals(List.of(newer, other), batch.meshes());
        assertEquals(NEWER_REQUEST, batch.request(FIRST));
        assertEquals(OTHER_REQUEST, batch.request(SECOND));
    }

    @Test
    void anEvictionDropsTheKeysMeshAndIsListedOnce() {
        batch.add(TestMeshes.of(FIRST, OccupancyMask.EMPTY), OLDER_REQUEST);
        batch.evict(FIRST);
        batch.evict(FIRST);

        assertTrue(batch.meshes().isEmpty());
        assertEquals(ONE_KEY, batch.evicted().size());
        assertTrue(batch.evicted().contains(FIRST));
    }

    @Test
    void aMeshAddedAfterItsEvictionLiftsTheEviction() {
        CellMesh mesh = TestMeshes.of(FIRST, OccupancyMask.EMPTY);
        batch.evict(FIRST);
        batch.add(mesh, OLDER_REQUEST);

        assertTrue(batch.evicted().isEmpty());
        assertEquals(List.of(mesh), batch.meshes());
    }
}
