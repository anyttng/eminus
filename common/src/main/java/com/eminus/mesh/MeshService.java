package com.eminus.mesh;

import com.eminus.cell.CellKey;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.cache.CellAccess;
import com.eminus.cell.cache.CellHandle;
import com.eminus.work.WorkService;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

public final class MeshService {
    private static final Direction[] FACES = Direction.values();

    private final WorkService<MeshScratch> work;
    private final CellAccess cells;
    private final MeshModels models;
    private final StateOpacity opacity;
    private final MeshListener listener;
    private final MeshQueue queue = new MeshQueue();

    public MeshService(WorkService<MeshScratch> work, CellAccess cells, MeshModels models,
            StateOpacity opacity, MeshListener listener) {
        this.work = work;
        this.cells = cells;
        this.models = models;
        this.opacity = opacity;
        this.listener = listener;
    }

    public void request(long key) {
        submit(MeshTask.fresh(key));
    }

    public void request(long key, @Nullable CellHandle held, int references) {
        submit(MeshTask.carrying(key, held, references));
    }

    public int backlog() {
        return queue.size();
    }

    public void drop() {
        queue.clear();
    }

    private void submit(MeshTask task) {
        queue.add(task);
        work.enqueue(this::take);
    }

    private void take(MeshScratch scratch) {
        MeshTask task = queue.poll();
        if (task != null) {
            build(task, scratch);
        }
    }

    void build(MeshTask task, MeshScratch scratch) {
        CellHandle[] handles = new CellHandle[FACES.length + 1];
        CellHandle held = task.held();

        try {
            handles[0] = held == null ? cells.open(task.key()) : held;
            handles[0].withCell(cell -> {
                scratch.voxels().load(cell);
                return null;
            });

            for (Direction face : FACES) {
                CellHandle handle = cells.open(CellKey.neighbour(task.key(), face));
                handles[face.ordinal() + 1] = handle;
                handle.withCell(cell -> {
                    scratch.voxels().loadNeighbour(face, cell);
                    return null;
                });
            }

            CellMesh mesh = new CellMesher(scratch, models).mesh(task.key(), opacity, () -> submit(task.retry()));
            if (mesh != null) {
                listener.meshed(mesh);
            }
        } finally {
            release(handles);
            releaseCarried(held, task.references());
        }
    }

    private void releaseCarried(@Nullable CellHandle held, int references) {
        for (int extra = 1; extra < references; extra++) {
            cells.release(held);
        }
    }

    private void release(CellHandle[] handles) {
        for (CellHandle handle : handles) {
            if (handle != null) {
                cells.release(handle);
            }
        }
    }
}
