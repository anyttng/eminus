package com.eminus.mesh;

import java.util.concurrent.atomic.AtomicBoolean;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.cache.CellAccess;
import com.eminus.cell.cache.CellHandle;
import com.eminus.work.WorkService;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

public final class MeshService {
    private static final Direction[] FACES = Direction.values();
    private static final int[][] DIAGONALS = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};

    private final WorkService<MeshScratch> work;
    private final CellAccess cells;
    private final ColumnCoverage coverage;
    private final CellFrame frame;
    private final MeshModels models;
    private final BiomeTints tints;
    private final int blendRadius;
    private final MeshOpacity opacity;
    private final MeshListener listener;
    private final MeshQueue queue = new MeshQueue();

    public MeshService(WorkService<MeshScratch> work, CellAccess cells, ColumnCoverage coverage, CellFrame frame,
            MeshModels models, BiomeTints tints, int blendRadius, MeshOpacity opacity, MeshListener listener) {
        this.work = work;
        this.cells = cells;
        this.coverage = coverage;
        this.frame = frame;
        this.models = models;
        this.tints = tints;
        this.blendRadius = blendRadius;
        this.opacity = opacity;
        this.listener = listener;
    }

    public void request(long key) {
        submit(MeshTask.fresh(key));
    }

    public void request(long key, @Nullable CellHandle held, int references, long request, float priority) {
        submit(MeshTask.carrying(key, held, references, request, priority));
    }

    public void release(CellHandle held, int references) {
        work.enqueue(scratch -> {
            for (int reference = 0; reference < references; reference++) {
                cells.release(held);
            }
        });
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
        CellHandle[] handles = new CellHandle[FACES.length + DIAGONALS.length + 1];
        CellHandle held = task.held();
        int radius = TintBlend.columns(blendRadius, task.level());

        try {
            handles[0] = held == null ? cells.open(task.key()) : held;
            int occupancy = handles[0].withCell(cell -> {
                scratch.voxels().load(cell);
                return cell.occupancy();
            });

            scratch.voxels().loadCoverage(coverage, task.key());

            for (Direction face : FACES) {
                CellHandle handle = cells.open(CellKey.neighbour(task.key(), face));
                handles[face.ordinal() + 1] = handle;
                handle.withCell(cell -> {
                    scratch.voxels().loadNeighbour(face, cell);
                    if (radius > 0 && face.getAxis().isHorizontal()) {
                        scratch.voxels().loadBiomes(cell, face.getStepX(), face.getStepZ(), radius);
                    }

                    return null;
                });
            }

            if (radius > 0) {
                for (int diagonal = 0; diagonal < DIAGONALS.length; diagonal++) {
                    int cellX = DIAGONALS[diagonal][0];
                    int cellZ = DIAGONALS[diagonal][1];
                    CellHandle handle = cells.open(CellKey.pack(task.level(), CellKey.x(task.key()) + cellX,
                            CellKey.y(task.key()), CellKey.z(task.key()) + cellZ));
                    handles[FACES.length + 1 + diagonal] = handle;
                    handle.withCell(cell -> {
                        scratch.voxels().loadBiomes(cell, cellX, cellZ, radius);
                        return null;
                    });
                }
            }

            scratch.blend().begin(scratch.voxels(), tints, task.key(), blendRadius);

            AtomicBoolean retried = new AtomicBoolean();
            CellMesh mesh = new CellMesher(scratch, models, frame).mesh(task.key(), occupancy,
                    opacity.at(CellKey.level(task.key())), () -> {
                if (retried.compareAndSet(false, true)) {
                    submit(task.retry());
                }
            });
            if (mesh != null) {
                listener.meshed(mesh, task.request());
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
