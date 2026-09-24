package com.eminus.render.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import com.eminus.api.v1.LevelState;
import com.eminus.api.v1.TreeState;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.EdgeMask;
import com.eminus.cell.FaceMask;
import com.eminus.cell.cache.CellHandle;
import com.eminus.ingest.CellChangeListener;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.FluidCorners;
import com.eminus.mesh.MeshListener;
import com.eminus.mesh.MeshSummary;
import com.eminus.settings.FarDistance;

import net.minecraft.core.Direction;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class TreeManager implements CellChangeListener, MeshListener {
    public static final String THREAD_NAME = "eminus-tree";
    public static final double STILL_BLOCKS = 0.5;

    private static final int THREAD_PRIORITY = Thread.NORM_PRIORITY - 1;
    private static final Direction[] FACES = Direction.values();
    private static final double STILL_BLOCKS_SQUARED = STILL_BLOCKS * STILL_BLOCKS;
    private static final float MATRIX_EPSILON = 1.0e-6F;
    private static final int ONE_REFERENCE = 1;

    private final TreeBuilds builds;
    private final TreeExtent extent;
    private final NodeTable nodes = new NodeTable(NodeTable.CAPACITY);
    private final TreeRing ring = new TreeRing();
    private final TreeRing.Columns columns = new Columns();
    private final TreeTraversal traversal;
    private final TreeCleaner cleaner = new TreeCleaner();
    private final BlockingQueue<TreeMessage> messages = new LinkedBlockingQueue<>();
    private final AtomicReference<CameraFrame> frames = new AtomicReference<>();
    private final TreeBatches batches = new TreeBatches();
    private final Matrix4f lastViewProjection = new Matrix4f();
    private final Thread thread = new Thread(this::serve, THREAD_NAME);

    private TreeBatch batch = new TreeBatch();
    private boolean treeChanged = true;
    private long requests;
    private int lastRequested;
    private boolean lastStarved;
    private int lastDrawn;
    private long pressureEvictions;
    private int refinements;
    private int outOfViewRefinements;
    private boolean outOfViewDue;
    private boolean outOfViewHeld;
    private @Nullable CameraFrame camera;
    private double lastEyeX;
    private double lastEyeY;
    private double lastEyeZ;

    private volatile long walks;
    private volatile boolean running = true;

    private TreeManager(TreeBuilds builds, TreeExtent extent) {
        this.builds = builds;
        this.extent = extent;
        traversal = new TreeTraversal(nodes, extent);
    }

    public static TreeManager start(TreeBuilds builds, TreeExtent extent) {
        TreeManager manager = new TreeManager(builds, extent);
        manager.thread.setDaemon(true);
        manager.thread.setPriority(THREAD_PRIORITY);
        manager.thread.start();
        return manager;
    }

    @Override
    public void changed(CellHandle handle, int faceMask, int edgeMask) {
        messages.add(new TreeMessage.CellChanged(handle, faceMask, edgeMask));
    }

    @Override
    public void covered(int chunkX, int chunkZ) {
        messages.add(new TreeMessage.ColumnCovered(chunkX, chunkZ));
    }

    @Override
    public void meshed(CellMesh mesh, long request) {
        messages.add(new TreeMessage.CellMeshed(mesh, request));
    }

    public void frame(CameraFrame frame) {
        if (frames.getAndSet(frame) == null) {
            messages.add(new TreeMessage.FrameReady());
        }
    }

    public CompletableFuture<List<long[]>> describe(List<long[]> rows) {
        CompletableFuture<List<long[]>> answer = new CompletableFuture<>();
        messages.add(new TreeMessage.Describe(rows, answer));
        return answer;
    }

    public TreeBatches batches() {
        return batches;
    }

    public long walks() {
        return walks;
    }

    public CompletableFuture<TreeState> snapshot() {
        CompletableFuture<TreeState> answer = new CompletableFuture<>();
        messages.add(new TreeMessage.Snapshot(answer));
        return answer;
    }

    public void stop() {
        running = false;
        thread.interrupt();

        try {
            thread.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }

        for (TreeMessage left : messages) {
            switch (left) {
                case TreeMessage.Snapshot snapshot -> snapshot.answer().completeExceptionally(stopped());
                case TreeMessage.Describe describe -> describe.answer().completeExceptionally(stopped());
                default -> {
                }
            }
        }
    }

    private void serve() {
        while (running) {
            TreeMessage message;

            try {
                message = messages.take();
            } catch (InterruptedException interrupted) {
                return;
            }

            apply(message);

            while ((message = messages.poll()) != null) {
                apply(message);
            }

            publish();
        }
    }

    private void apply(TreeMessage message) {
        switch (message) {
            case TreeMessage.CellChanged changed ->
                    applyChange(changed.handle(), changed.faceMask(), changed.edgeMask());
            case TreeMessage.CellMeshed meshed -> applyMesh(meshed.mesh(), meshed.request());
            case TreeMessage.ColumnCovered covered -> applyCovered(covered.chunkX(), covered.chunkZ());
            case TreeMessage.FrameReady ready -> applyFrame();
            case TreeMessage.Describe describe -> describe.answer().complete(applyDescribe(describe.rows()));
            case TreeMessage.Snapshot snapshot -> snapshot.answer().complete(state());
        }
    }

    private List<long[]> applyDescribe(List<long[]> rows) {
        for (long[] row : rows) {
            TreeNode node = nodes.get(CellKey.pack((int) row[NodeRow.LEVEL], (int) row[NodeRow.CELL_X],
                    (int) row[NodeRow.CELL_Y], (int) row[NodeRow.CELL_Z]));
            MeshSummary mesh = node == null ? null : node.mesh();
            row[NodeRow.NODE_PRESENT] = node == null ? 0 : 1;
            row[NodeRow.MESHED] = mesh == null ? 0 : 1;
            row[NodeRow.MESH_QUADS] = mesh == null ? 0 : mesh.quadCount();
            row[NodeRow.OCCUPANCY] = node == null ? 0 : node.occupancy();
            row[NodeRow.BUILDING] = node != null && node.building() ? 1 : 0;
            row[NodeRow.REQUESTED] = node == null ? 0 : node.requestedOctants();
            row[NodeRow.CHILDREN_READY] = node != null && node.childrenReady() ? 1 : 0;
            row[NodeRow.LAST_SEEN] = node == null ? 0 : node.lastSeen();
            row[NodeRow.WALKS] = walks;
        }

        return rows;
    }

    private static IllegalStateException stopped() {
        return new IllegalStateException("The far renderer stopped.");
    }

    private TreeState state() {
        int[] levelNodes = new int[DetailLevel.COUNT];
        int[] levelMeshed = new int[DetailLevel.COUNT];
        long[] levelQuads = new long[DetailLevel.COUNT];
        int building = 0;

        for (TreeNode node : nodes.all()) {
            int level = node.level() - DetailLevel.MIN;
            levelNodes[level]++;
            if (node.building()) {
                building++;
            }

            MeshSummary mesh = node.mesh();
            if (mesh != null) {
                levelMeshed[level]++;
                levelQuads[level] += mesh.quadCount();
            }
        }

        List<LevelState> levels = new ArrayList<>(DetailLevel.COUNT);
        for (int level = 0; level < DetailLevel.COUNT; level++) {
            levels.add(new LevelState(level + DetailLevel.MIN, levelNodes[level], levelMeshed[level], levelQuads[level]));
        }

        int backlog = builds.backlog();
        boolean batchWaiting = !batch.isEmpty() || batches.waiting();
        boolean settled = building == 0 && !treeChanged && lastRequested == 0 && !lastStarved && backlog == 0
                && !batchWaiting;

        return new TreeState(walks, nodes.size(), nodes.free(), lastDrawn, backlog, building, lastRequested,
                lastStarved, treeChanged, batchWaiting, pressureEvictions, settled, List.copyOf(levels));
    }

    private void applyChange(CellHandle handle, int faceMask, int edgeMask) {
        long key = handle.key();
        TreeNode node = nodes.get(key);
        if (node == null) {
            builds.release(handle, ONE_REFERENCE);
        } else {
            node.hold(handle);
            requestBuild(node);
        }

        requestNeighbours(key, faceMask);
        if (CellKey.level(key) == FluidCorners.LEVEL) {
            requestAcrossEdges(key, edgeMask);
        }
    }

    private void applyCovered(int chunkX, int chunkZ) {
        int firstBlockX = chunkX * FarDistance.BLOCKS_PER_CHUNK;
        int firstBlockZ = chunkZ * FarDistance.BLOCKS_PER_CHUNK;

        for (int level = extent.lowestLevel(); level <= DetailLevel.MAX; level++) {
            int side = DetailLevel.blocksPerCell(level);
            int heightCells = extent.heightCells() << (DetailLevel.MAX - level);
            int lastCellX = Math.floorDiv(firstBlockX + FarDistance.BLOCKS_PER_CHUNK, side);
            int lastCellZ = Math.floorDiv(firstBlockZ + FarDistance.BLOCKS_PER_CHUNK, side);

            for (int cellX = Math.floorDiv(firstBlockX - 1, side); cellX <= lastCellX; cellX++) {
                for (int cellZ = Math.floorDiv(firstBlockZ - 1, side); cellZ <= lastCellZ; cellZ++) {
                    for (int cellY = 0; cellY < heightCells; cellY++) {
                        TreeNode node = nodes.get(CellKey.pack(level, cellX, cellY, cellZ));
                        if (node != null) {
                            requestBuild(node);
                        }
                    }
                }
            }
        }
    }

    private void applyMesh(CellMesh mesh, long request) {
        TreeNode node = nodes.get(mesh.key());
        if (node == null || node.request() != request) {
            return;
        }

        if (!node.isRoot()) {
            settleRefinement(node);
        }

        node.meshed(mesh.summary());
        pruneStaleChildren(node);
        batch.add(mesh);
        treeChanged = true;

        if (node.takeRebuild()) {
            dispatch(node, priority(node), false);
        }
    }

    private void applyFrame() {
        CameraFrame camera = frames.getAndSet(null);
        if (camera == null) {
            return;
        }

        this.camera = camera;

        if (ring.update(camera.eyeX(), camera.eyeZ(), camera.farCells(), columns)) {
            treeChanged = true;
        }

        boolean still = still(camera);
        if (!treeChanged && still && !outOfViewDue) {
            return;
        }

        if (camera.arenaPressure()) {
            outOfViewHeld = true;
        } else if (!still) {
            outOfViewHeld = false;
        }

        long walk = walks + 1;
        int free = nodes.free();
        int budget = Math.min(RequestBudget.perWalk(refinements), free);
        int outOfViewBudget = still && !outOfViewHeld
                ? Math.min(RequestBudget.perWalk(refinements + outOfViewRefinements), free)
                : 0;
        RenderList walked = traversal.walk(nodes.roots(), camera, budget, outOfViewBudget, walk);
        batch.renderList(walked);
        List<TreeNode> requested = traversal.requested();
        List<TreeNode> outOfView = traversal.outOfViewRequested();
        lastRequested = requested.size() + outOfView.size();
        lastStarved = traversal.starved();
        lastDrawn = walked.meshes().size();
        outOfViewDue = !still;

        for (int index = 0; index < requested.size(); index++) {
            dispatch(requested.get(index), traversal.requestedPriority(index), false);
        }

        for (int index = 0; index < outOfView.size(); index++) {
            dispatch(outOfView.get(index), ProjectedSize.outOfView(traversal.outOfViewSize(index)), true);
        }

        List<TreeNode> stale = cleaner.pick(nodes.all(), camera, extent.frame(), walk);
        for (TreeNode node : stale) {
            if (nodes.get(node.key()) == node) {
                evict(node);
                pressureEvictions++;
            }
        }

        treeChanged = !stale.isEmpty();
        lastEyeX = camera.eyeX();
        lastEyeY = camera.eyeY();
        lastEyeZ = camera.eyeZ();
        lastViewProjection.set(camera.viewProjection());
        walks = walk;
    }

    private boolean still(CameraFrame camera) {
        double dx = camera.eyeX() - lastEyeX;
        double dy = camera.eyeY() - lastEyeY;
        double dz = camera.eyeZ() - lastEyeZ;

        return dx * dx + dy * dy + dz * dz < STILL_BLOCKS_SQUARED
                && camera.viewProjection().equals(lastViewProjection, MATRIX_EPSILON);
    }

    private void pruneStaleChildren(TreeNode node) {
        int stale = node.requestedOctants() & ~node.occupancy();

        while (stale != 0) {
            int octant = Integer.numberOfTrailingZeros(stale);
            TreeNode child = node.child(octant);
            if (child != null) {
                evict(child);
            } else {
                node.detach(octant);
            }

            stale &= stale - 1;
        }
    }

    private void evict(TreeNode node) {
        nodes.remove(node, removed -> {
            if (removed.mesh() != null) {
                batch.evict(removed.key());
            }

            if (removed.building() && !removed.isRoot()) {
                settleRefinement(removed);
            }

            CellHandle pending = removed.pending();
            if (pending != null) {
                builds.release(pending, removed.pendingReferences());
            }
        });
        treeChanged = true;
    }

    private void requestNeighbours(long key, int faceMask) {
        for (Direction face : FACES) {
            if ((faceMask & FaceMask.bit(face)) == 0) {
                continue;
            }

            TreeNode neighbour = nodes.get(CellKey.neighbour(key, face));
            if (neighbour != null) {
                requestBuild(neighbour);
            }
        }
    }

    private void requestAcrossEdges(long key, int edgeMask) {
        int remaining = edgeMask;

        while (remaining != 0) {
            int slot = Integer.numberOfTrailingZeros(remaining);
            TreeNode neighbour = nodes.get(CellKey.pack(CellKey.level(key), CellKey.x(key) + EdgeMask.stepX(slot),
                    CellKey.y(key) + EdgeMask.stepY(slot), CellKey.z(key) + EdgeMask.stepZ(slot)));
            if (neighbour != null) {
                requestBuild(neighbour);
            }

            remaining &= remaining - 1;
        }
    }

    private void requestBuild(TreeNode node) {
        if (node.building()) {
            node.markRebuild();
            return;
        }

        dispatch(node, priority(node), false);
    }

    private float priority(TreeNode node) {
        return camera == null ? ProjectedSize.UNKNOWN : ProjectedSize.of(extent.frame(), node.key(), camera);
    }

    private void dispatch(TreeNode node, float priority, boolean outOfView) {
        if (!node.isRoot() && !node.building()) {
            if (outOfView) {
                outOfViewRefinements++;
            } else {
                refinements++;
            }
        }

        CellHandle handle = node.pending();
        int references = node.pendingReferences();
        long request = ++requests;
        node.clearPending();
        node.startBuild(request, outOfView);
        builds.build(node.key(), handle, references, request, priority);
    }

    private void settleRefinement(TreeNode node) {
        if (node.outOfViewBuild()) {
            outOfViewRefinements--;
        } else {
            refinements--;
        }
    }

    private void publish() {
        if (!batch.isEmpty() && batches.offer(batch)) {
            batch = new TreeBatch();
        }
    }

    private final class Columns implements TreeRing.Columns {
        @Override
        public void added(int cellX, int cellZ) {
            for (int cellY = 0; cellY < extent.heightCells(); cellY++) {
                TreeNode root = nodes.root(CellKey.pack(DetailLevel.MAX, cellX, cellY, cellZ));
                if (root.mesh() == null && !root.building()) {
                    dispatch(root, priority(root), false);
                }
            }

            treeChanged = true;
        }

        @Override
        public void removed(int cellX, int cellZ) {
            for (int cellY = 0; cellY < extent.heightCells(); cellY++) {
                TreeNode root = nodes.get(CellKey.pack(DetailLevel.MAX, cellX, cellY, cellZ));
                if (root != null) {
                    evict(root);
                }
            }
        }
    }
}
