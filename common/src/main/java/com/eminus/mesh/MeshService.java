package com.eminus.mesh;

import com.eminus.cell.CellKey;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.cache.CellAccess;
import com.eminus.cell.cache.CellHandle;
import com.eminus.work.WorkService;

import net.minecraft.core.Direction;

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

        try {
            handles[0] = cells.open(task.key());
            handles[0].withCell(cell -> {
                scratch.voxels().load(cell);
                return null;
            });

            for (Direction face : FACES) {
                CellHandle handle = cells.open(neighbourKey(task.key(), face));
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
        }
    }

    private void release(CellHandle[] handles) {
        for (CellHandle handle : handles) {
            if (handle != null) {
                cells.release(handle);
            }
        }
    }

    static long neighbourKey(long key, Direction face) {
        int level = CellKey.level(key);
        int step = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;

        return switch (face.getAxis()) {
            case X -> CellKey.pack(level, CellKey.x(key) + step, CellKey.y(key), CellKey.z(key));
            case Y -> CellKey.pack(level, CellKey.x(key), CellKey.y(key) + step, CellKey.z(key));
            case Z -> CellKey.pack(level, CellKey.x(key), CellKey.y(key), CellKey.z(key) + step);
        };
    }
}
