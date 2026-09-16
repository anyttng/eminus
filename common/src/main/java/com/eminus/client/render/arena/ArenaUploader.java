package com.eminus.client.render.arena;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import com.eminus.Eminus;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshBuffer;
import com.eminus.mesh.QuadGroups;
import com.eminus.render.arena.ArenaSizing;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.StagingBuffer;

public final class ArenaUploader implements AutoCloseable {
    public static final int MAX_QUADS = QuadGroups.COUNT * MeshBuffer.MAX_QUADS_PER_GROUP;
    public static final int MAX_MESH_BYTES = MAX_QUADS * ArenaSizing.QUAD_BYTES;

    private static final String NAME = "eminus-arena";
    private static final int STAGING_MESHES = 4;
    private static final int STAGING_BYTES = MAX_MESH_BYTES * STAGING_MESHES;

    private final StagingBuffer staging;
    private final ByteBuffer scratch = ByteBuffer.allocateDirect(MAX_MESH_BYTES).order(ByteOrder.nativeOrder());
    private final LongBuffer quads = scratch.asLongBuffer();

    private int uploaded;

    private ArenaUploader(StagingBuffer staging) {
        this.staging = staging;
    }

    public static ArenaUploader create() {
        RenderSystem.assertOnRenderThread();
        return new ArenaUploader(StagingBuffer.create(NAME, RenderSystem.getDevice(), STAGING_BYTES));
    }

    public int uploaded() {
        return uploaded;
    }

    public List<ArenaUpload> upload(GpuBuffer target, List<ArenaUpload> uploads) {
        RenderSystem.assertOnRenderThread();
        List<ArenaUpload> dropped = new ArrayList<>();
        uploaded = 0;

        int index = 0;
        while (index < uploads.size()) {
            index = uploadRun(target, uploads, index, dropped);
        }

        return dropped;
    }

    private int uploadRun(GpuBuffer target, List<ArenaUpload> uploads, int from, List<ArenaUpload> dropped) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        List<StagingBuffer.BufferHandle> handles = new ArrayList<>();
        int index = from;

        try (StagingBuffer.Uploader uploader = staging.startUploading(encoder)) {
            try {
                while (index < uploads.size()) {
                    ArenaUpload upload = uploads.get(index);
                    StagingBuffer.BufferHandle handle = staging.tryAppend(fill(upload.mesh()));

                    if (handle == null) {
                        if (index > from) {
                            break;
                        }

                        Eminus.LOGGER.warn("Cell {} does not fit the staging buffer at {} quads and is dropped",
                                upload.mesh().key(), upload.mesh().quadCount());
                        dropped.add(upload);
                        index++;
                        continue;
                    }

                    handles.add(handle);
                    uploader.copyTo(handle, target, upload.byteOffset());
                    uploaded++;
                    index++;
                }
            } finally {
                handles.forEach(StagingBuffer.BufferHandle::close);
            }
        }

        return index;
    }

    private ByteBuffer fill(CellMesh mesh) {
        quads.clear();
        quads.put(mesh.quads(), 0, mesh.quadCount());
        return scratch.clear().limit(mesh.quadCount() * ArenaSizing.QUAD_BYTES);
    }

    @Override
    public void close() {
        staging.close();
    }
}
