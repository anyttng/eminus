package com.eminus.mesh;

import java.util.concurrent.atomic.AtomicBoolean;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;
import com.eminus.cell.cache.CellAccess;
import com.eminus.cell.cache.CellHandle;
import com.eminus.work.WorkService;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

public final class MeshService {
    private static final Direction[] FACES = Direction.values();
    private static final int[][] DIAGONALS = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
    private static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final int LAST = DetailLevel.VOXELS_PER_SIDE - 1;

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
        CellHandle[] handles = new CellHandle[1 + FACES.length + DIAGONALS.length + HORIZONTALS.length
                + DIAGONALS.length];
        CellHandle held = task.held();
        int radius = TintBlend.columns(blendRadius, task.level());
        boolean corners = task.level() == FluidCorners.LEVEL;
        int x = CellKey.x(task.key());
        int y = CellKey.y(task.key());
        int z = CellKey.z(task.key());

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

            int slot = FACES.length + 1;
            if (radius > 0 || corners) {
                for (int[] diagonal : DIAGONALS) {
                    int cellX = diagonal[0];
                    int cellZ = diagonal[1];
                    CellHandle handle = cells.open(CellKey.pack(task.level(), x + cellX, y, z + cellZ));
                    handles[slot++] = handle;
                    handle.withCell(cell -> {
                        if (radius > 0) {
                            scratch.voxels().loadBiomes(cell, cellX, cellZ, radius);
                        }

                        scratch.voxels().loadDiagonal(cellX, cellZ, cell);
                        return null;
                    });
                }
            }

            if (corners) {
                for (Direction side : HORIZONTALS) {
                    if (topEdgeHoldsFluid(scratch.voxels(), side.getStepX(), side.getStepZ())) {
                        CellHandle handle = cells.open(
                                CellKey.pack(task.level(), x + side.getStepX(), y + 1, z + side.getStepZ()));
                        handles[slot++] = handle;
                        handle.withCell(cell -> {
                            scratch.voxels().loadAboveSide(side, cell);
                            return null;
                        });
                    }
                }

                for (int[] diagonal : DIAGONALS) {
                    int cellX = diagonal[0];
                    int cellZ = diagonal[1];
                    if (topEdgeHoldsFluid(scratch.voxels(), cellX, cellZ)) {
                        CellHandle handle = cells.open(CellKey.pack(task.level(), x + cellX, y + 1, z + cellZ));
                        handles[slot++] = handle;
                        handle.withCell(cell -> {
                            scratch.voxels().loadAboveCorner(cellX, cellZ, cell);
                            return null;
                        });
                    }
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

    private boolean topEdgeHoldsFluid(CellVoxels voxels, int stepX, int stepZ) {
        int fromX = stepX > 0 ? LAST : 0;
        int toX = stepX < 0 ? 0 : LAST;
        int fromZ = stepZ > 0 ? LAST : 0;
        int toZ = stepZ < 0 ? 0 : LAST;

        for (int z = fromZ; z <= toZ; z++) {
            for (int x = fromX; x <= toX; x++) {
                long entry = voxels.inside(x, LAST, z);
                if (!VoxelEntry.isAir(entry) && models.holdsFluid(VoxelEntry.state(entry))) {
                    return true;
                }
            }
        }

        return false;
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
