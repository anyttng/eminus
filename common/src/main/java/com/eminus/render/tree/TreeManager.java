package com.eminus.render.tree;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.FaceMask;
import com.eminus.cell.cache.CellHandle;
import com.eminus.ingest.CellChangeListener;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshListener;

import net.minecraft.core.Direction;

import org.joml.Matrix4f;

public final class TreeManager implements CellChangeListener, MeshListener {
    public static final String THREAD_NAME = "eminus-tree";
    public static final double STILL_BLOCKS = 0.5;

    private static final int THREAD_PRIORITY = Thread.NORM_PRIORITY - 1;
    private static final Direction[] FACES = Direction.values();
    private static final double STILL_BLOCKS_SQUARED = STILL_BLOCKS * STILL_BLOCKS;
    private static final float MATRIX_EPSILON = 1.0e-6F;
    private static final int ONE_REFERENCE = 1;
    private static final String PROBE = "[eminus-tree]";

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
    public void changed(CellHandle handle, int faceMask) {
        messages.add(new TreeMessage.CellChanged(handle, faceMask));
    }

    @Override
    public void meshed(CellMesh mesh) {
        messages.add(new TreeMessage.CellMeshed(mesh));
    }

    public void frame(CameraFrame frame) {
        if (frames.getAndSet(frame) == null) {
            messages.add(new TreeMessage.FrameReady());
        }
    }

    public void describe(long[] keys) {
        messages.add(new TreeMessage.Describe(keys));
    }

    public TreeBatches batches() {
        return batches;
    }

    public long walks() {
        return walks;
    }

    public void stop() {
        running = false;
        thread.interrupt();

        try {
            thread.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
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
            case TreeMessage.CellChanged changed -> applyChange(changed.handle(), changed.faceMask());
            case TreeMessage.CellMeshed meshed -> applyMesh(meshed.mesh());
            case TreeMessage.FrameReady ready -> applyFrame();
            case TreeMessage.Describe describe -> applyDescribe(describe.keys());
        }
    }

    private void applyDescribe(long[] keys) {
        for (long key : keys) {
            TreeNode node = nodes.get(key);
            CellMesh mesh = node == null ? null : node.mesh();
            Eminus.LOGGER.info("{} node level={} x={} y={} z={} present={} meshed={} quads={} occupancy={} "
                    + "building={} requested={} ready={} seen={} walks={}", PROBE, CellKey.level(key),
                    CellKey.x(key), CellKey.y(key), CellKey.z(key), node == null ? 0 : 1, mesh == null ? 0 : 1,
                    mesh == null ? 0 : mesh.quadCount(), node == null ? 0 : node.occupancy(),
                    node != null && node.building() ? 1 : 0, node == null ? 0 : node.requestedOctants(),
                    node != null && node.childrenReady() ? 1 : 0, node == null ? 0 : node.lastSeen(), walks);
        }
    }

    private void applyChange(CellHandle handle, int faceMask) {
        TreeNode node = nodes.get(handle.key());
        if (node == null) {
            builds.release(handle, ONE_REFERENCE);
            return;
        }

        node.hold(handle);
        requestBuild(node);
        requestNeighbours(handle.key(), faceMask);
    }

    private void applyMesh(CellMesh mesh) {
        TreeNode node = nodes.get(mesh.key());
        if (node == null) {
            return;
        }

        node.meshed(mesh);
        pruneStaleChildren(node);
        batch.add(mesh);
        treeChanged = true;

        if (node.takeRebuild()) {
            dispatch(node);
        }
    }

    private void applyFrame() {
        CameraFrame camera = frames.getAndSet(null);
        if (camera == null) {
            return;
        }

        if (ring.update(camera.eyeX(), camera.eyeZ(), camera.farCells(), columns)) {
            treeChanged = true;
        }

        if (!treeChanged && still(camera)) {
            return;
        }

        long walk = walks + 1;
        int budget = Math.min(RequestBudget.perWalk(builds.backlog()), nodes.free());
        RenderList walked = traversal.walk(nodes.roots(), camera, budget, walk);
        batch.renderList(walked);

        for (TreeNode child : traversal.requested()) {
            dispatch(child);
        }

        List<TreeNode> stale = cleaner.pick(nodes.all(), camera.arenaPressure());
        for (TreeNode node : stale) {
            if (nodes.get(node.key()) == node) {
                evict(node);
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

    private void requestBuild(TreeNode node) {
        if (node.building()) {
            node.markRebuild();
            return;
        }

        dispatch(node);
    }

    private void dispatch(TreeNode node) {
        CellHandle handle = node.pending();
        int references = node.pendingReferences();
        node.clearPending();
        node.startBuild();
        builds.build(node.key(), handle, references);
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
                    dispatch(root);
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
