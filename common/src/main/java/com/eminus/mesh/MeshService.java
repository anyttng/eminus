package com.eminus.mesh;

import java.util.concurrent.atomic.AtomicBoolean;

import com.eminus.Eminus;
import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;
import com.eminus.cell.cache.CellAccess;
import com.eminus.cell.cache.CellHandle;
import com.eminus.work.WorkService;

import org.jspecify.annotations.Nullable;

public final class MeshService {
    private static final int STEPS_PER_AXIS = 3;
    private static final int[][] NEIGHBOURS = neighbours();
    private static final int LAST = DetailLevel.VOXELS_PER_SIDE - 1;

    private final WorkService<MeshScratch> work;
    private final CellAccess cells;
    private final ColumnCoverage coverage;
    private final CellFrame frame;
    private final MeshModels models;
    private final BiomeTints tints;
    private final int blendRadius;
    private final MeshOpacity opacity;
    private final MeshOcclusion occlusion;
    private final MeshListener listener;
    private final int urgentWeight;
    private final MeshQueue queue = new MeshQueue();

    public MeshService(WorkService<MeshScratch> work, CellAccess cells, ColumnCoverage coverage, CellFrame frame,
            MeshModels models, BiomeTints tints, int blendRadius, MeshOpacity opacity, MeshOcclusion occlusion,
            MeshListener listener, int urgentWeight) {
        this.work = work;
        this.urgentWeight = urgentWeight;
        this.cells = cells;
        this.coverage = coverage;
        this.frame = frame;
        this.models = models;
        this.tints = tints;
        this.blendRadius = blendRadius;
        this.opacity = opacity;
        this.occlusion = occlusion;
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

    public void urgent(boolean urgent) {
        if (urgent) {
            work.raise(urgentWeight);
        } else {
            work.lower();
        }
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
        CellHandle[] handles = new CellHandle[1 + NEIGHBOURS.length];
        CellHandle held = task.held();
        int radius = TintBlend.columns(blendRadius, task.level());
        boolean corners = task.level() == FluidCorners.LEVEL;
        int x = CellKey.x(task.key());
        int y = CellKey.y(task.key());
        int z = CellKey.z(task.key());

        try {
            handles[0] = held == null ? cells.open(task.key()) : held;
            long[] grandchildren = new long[1];
            int occupancy = handles[0].withCell(cell -> {
                scratch.voxels().load(cell);
                grandchildren[0] = cell.grandchildOccupancy();
                return cell.occupancy();
            });

            scratch.voxels().loadCoverage(coverage, task.key());

            CellVoxels voxels = scratch.voxels();
            for (int at = 0; at < NEIGHBOURS.length; at++) {
                int[] step = NEIGHBOURS[at];
                int stepX = step[0];
                int stepY = step[1];
                int stepZ = step[2];
                if (!reads(voxels, stepX, stepY, stepZ, radius, corners)) {
                    continue;
                }

                CellHandle handle = cells.open(CellKey.pack(task.level(), x + stepX, y + stepY, z + stepZ));
                handles[at + 1] = handle;
                handle.withCell(cell -> {
                    voxels.loadRegion(stepX, stepY, stepZ, cell);
                    if (stepX == 0 && stepY < 0 && stepZ == 0 && edgeFluidReachesBottom(voxels)) {
                        voxels.loadBelow(cell);
                    }

                    if (radius > 0 && stepY == 0) {
                        voxels.loadBiomes(cell, stepX, stepZ, radius);
                    }

                    return null;
                });
            }

            scratch.begin(frame, task.key(), tints, blendRadius);

            AtomicBoolean retried = new AtomicBoolean();
            CellMesh mesh = new CellMesher(scratch, models).mesh(task.key(), occupancy, opacity.at(task.level()),
                    occlusion.at(task.level()), () -> {
                if (retried.compareAndSet(false, true)) {
                    submit(task.retry());
                }
            });
            if (mesh != null) {
                CellMesh masked = mesh.withGrandchildren(grandchildren[0])
                        .readingBelow(scratch.voxels().belowLoaded());
                listener.meshed(coverage.missingColumns(task.key()) ? masked.lackingColumns() : masked,
                        task.request());
            }
        } catch (RuntimeException failure) {
            long key = task.key();
            Eminus.LOGGER.warn("Meshing cell {} at level {} ({}, {}, {}) threw, so the tree asks for it again: {}",
                    key, CellKey.level(key), CellKey.x(key), CellKey.y(key), CellKey.z(key), failure.toString());
            listener.failed(key, task.request());
        } finally {
            release(handles);
            releaseCarried(held, task.references());
        }
    }

    private static int[][] neighbours() {
        int[][] steps = new int[STEPS_PER_AXIS * STEPS_PER_AXIS * STEPS_PER_AXIS - 1][];
        int at = 0;
        for (int stepY = -1; stepY <= 1; stepY++) {
            for (int stepZ = -1; stepZ <= 1; stepZ++) {
                for (int stepX = -1; stepX <= 1; stepX++) {
                    if (stepX != 0 || stepY != 0 || stepZ != 0) {
                        steps[at++] = new int[] {stepX, stepY, stepZ};
                    }
                }
            }
        }

        return steps;
    }

    private boolean reads(CellVoxels voxels, int stepX, int stepY, int stepZ, int radius, boolean corners) {
        int across = Math.abs(stepX) + Math.abs(stepY) + Math.abs(stepZ);
        if (across == 1) {
            return true;
        }

        if (stepY == 0 && (radius > 0 || corners)) {
            return true;
        }

        if (stepY > 0 && corners && topEdgeHoldsFluid(voxels, stepX, stepZ)) {
            return true;
        }

        return voxels.holdsNear(stepX, stepY, stepZ);
    }

    private boolean topEdgeHoldsFluid(CellVoxels voxels, int stepX, int stepZ) {
        int fromX = stepX > 0 ? LAST : 0;
        int toX = stepX < 0 ? 0 : LAST;
        int fromZ = stepZ > 0 ? LAST : 0;
        int toZ = stepZ < 0 ? 0 : LAST;

        for (int z = fromZ; z <= toZ; z++) {
            for (int x = fromX; x <= toX; x++) {
                long entry = voxels.entry(x, LAST, z);
                if (!VoxelEntry.isAir(entry) && models.holdsFluid(VoxelEntry.state(entry))) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean edgeFluidReachesBottom(CellVoxels voxels) {
        for (int z = 0; z <= LAST; z++) {
            for (int x = 0; x <= LAST; x++) {
                if (!voxels.covered(x, z) || !besideUncovered(voxels, x, z)) {
                    continue;
                }

                long entry = voxels.entry(x, 0, z);
                if (!VoxelEntry.isAir(entry) && models.holdsFluid(VoxelEntry.state(entry))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean besideUncovered(CellVoxels voxels, int x, int z) {
        return !voxels.covered(x - 1, z) || !voxels.covered(x + 1, z) || !voxels.covered(x, z - 1)
                || !voxels.covered(x, z + 1);
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
