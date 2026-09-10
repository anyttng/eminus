package com.eminus.render.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import com.eminus.cell.CellKey;
import com.eminus.cell.FaceMask;
import com.eminus.cell.cache.CellHandle;
import com.eminus.ingest.CellChangeListener;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshListener;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

public final class TreeManager implements CellChangeListener, MeshListener {
    public static final String THREAD_NAME = "eminus-tree";

    private static final int THREAD_PRIORITY = Thread.NORM_PRIORITY - 1;
    private static final Direction[] FACES = Direction.values();

    private final TreeBuilds builds;
    private final NodeTable nodes = new NodeTable();
    private final BlockingQueue<TreeMessage> messages = new LinkedBlockingQueue<>();
    private final TreeBatches batches = new TreeBatches();
    private final AtomicReference<RenderList> renderList = new AtomicReference<>(RenderList.EMPTY);
    private final Thread thread = new Thread(this::serve, THREAD_NAME);

    private TreeBatch batch = new TreeBatch();
    private boolean renderListStale;

    private volatile boolean running = true;

    private TreeManager(TreeBuilds builds) {
        this.builds = builds;
    }

    public static TreeManager start(TreeBuilds builds) {
        TreeManager manager = new TreeManager(builds);
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

    public TreeBatches batches() {
        return batches;
    }

    public RenderList renderList() {
        return renderList.get();
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
        }
    }

    private void applyChange(CellHandle handle, int faceMask) {
        TreeNode node = nodes.node(handle.key());
        node.hold(handle);
        requestBuild(node);
        requestNeighbours(handle.key(), faceMask);
    }

    private void applyMesh(CellMesh mesh) {
        TreeNode node = nodes.node(mesh.key());
        node.meshed(mesh);
        batch.add(mesh);
        renderListStale = true;

        if (node.takeRebuild()) {
            dispatch(node);
        }
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

        if (renderListStale) {
            renderList.set(new RenderList(meshes()));
            renderListStale = false;
        }
    }

    private List<CellMesh> meshes() {
        List<CellMesh> listed = new ArrayList<>(nodes.size());

        for (TreeNode node : nodes.all()) {
            CellMesh mesh = node.mesh();
            if (mesh != null) {
                listed.add(mesh);
            }
        }

        return List.copyOf(listed);
    }
}
