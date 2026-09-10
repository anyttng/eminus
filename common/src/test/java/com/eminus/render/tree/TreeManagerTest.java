package com.eminus.render.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.eminus.cell.CellKey;
import com.eminus.cell.FaceMask;
import com.eminus.cell.cache.CellCache;
import com.eminus.cell.cache.CellHandle;
import com.eminus.mesh.CellMesh;
import com.eminus.store.FakeCellStore;
import com.eminus.work.WorkerHarness;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class TreeManagerTest {
    private static final int WORKER_THREADS = 2;
    private static final long AWAIT_MILLIS = 10_000L;
    private static final int LEVEL = 0;
    private static final long KEY = CellKey.pack(LEVEL, 1, 2, 3);
    private static final long WEST = CellKey.neighbour(KEY, Direction.WEST);

    private final FakeCellStore store = new FakeCellStore();
    private final WorkerHarness harness = new WorkerHarness(WORKER_THREADS);
    private final CellCache cache = new CellCache(store, handle -> { }, () -> 0L);
    private final FakeBuilds builds = new FakeBuilds();
    private final TreeManager manager = TreeManager.start(builds);

    @AfterEach
    void stopEverything() {
        manager.stop();
        harness.close();
    }

    @Test
    void aSecondChangeBeforeTheBuildLandsQueuesNoSecondBuild() {
        CellHandle handle = open(KEY);
        manager.changed(handle, FaceMask.NONE);

        FakeBuilds.Call first = builds.take();
        assertEquals(KEY, first.key());
        assertSame(handle, first.handle());
        assertEquals(1, first.references());

        manager.changed(open(KEY), FaceMask.NONE);
        manager.changed(open(KEY), FaceMask.NONE);
        manager.meshed(CellMesh.empty(KEY));

        FakeBuilds.Call rebuilt = builds.take();
        assertEquals(KEY, rebuilt.key());
        assertSame(handle, rebuilt.handle());
        assertEquals(2, rebuilt.references());
        assertTrue(builds.idle());
    }

    @Test
    void aFinishedMeshIsTakenAsOneBatchAndOnlyOnce() {
        CellMesh mesh = CellMesh.empty(KEY);
        manager.meshed(mesh);

        TreeBatch batch = awaitBatch();
        assertEquals(List.of(mesh), batch.meshes());
        assertNull(manager.batches().take());
        assertTrue(builds.idle());
    }

    @Test
    void theRenderListCarriesEveryMeshedNodeOnceWithItsNewestMesh() {
        CellMesh first = CellMesh.empty(KEY);
        CellMesh second = CellMesh.empty(KEY);

        manager.changed(open(WEST), FaceMask.NONE);
        manager.meshed(first);
        manager.meshed(second);

        List<CellMesh> listed = awaitRenderList(list -> list.meshes().contains(second)).meshes();
        assertEquals(1, listed.size());
        assertSame(second, listed.get(0));
    }

    @Test
    void aBoundaryChangeRebuildsOnlyTheNeighboursThatAreNodes() {
        manager.meshed(CellMesh.empty(WEST));
        awaitRenderList(list -> list.meshes().size() == 1);

        CellHandle handle = open(KEY);
        manager.changed(handle, FaceMask.WEST | FaceMask.EAST);

        FakeBuilds.Call own = builds.take();
        assertEquals(KEY, own.key());
        assertSame(handle, own.handle());

        FakeBuilds.Call neighbour = builds.take();
        assertEquals(WEST, neighbour.key());
        assertNull(neighbour.handle());
        assertEquals(0, neighbour.references());
        assertTrue(builds.idle());
    }

    private CellHandle open(long key) {
        return harness.call(() -> cache.open(key));
    }

    private TreeBatch awaitBatch() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);

        while (System.nanoTime() < deadline) {
            TreeBatch batch = manager.batches().take();
            if (batch != null) {
                return batch;
            }

            Thread.onSpinWait();
        }

        throw new IllegalStateException("No batch arrived within " + AWAIT_MILLIS + " ms.");
    }

    private RenderList awaitRenderList(Predicate<RenderList> ready) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AWAIT_MILLIS);

        while (System.nanoTime() < deadline) {
            RenderList list = manager.renderList();
            if (ready.test(list)) {
                return list;
            }

            Thread.onSpinWait();
        }

        throw new IllegalStateException("The render list did not settle within " + AWAIT_MILLIS + " ms.");
    }
}
