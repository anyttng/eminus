package com.eminus.client.render.arena;

import java.lang.reflect.Constructor;
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

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.StagingBuffer;

import org.jspecify.annotations.Nullable;

public final class ArenaUploader implements AutoCloseable {
    public static final int MAX_QUADS = QuadGroups.COUNT * MeshBuffer.MAX_QUADS_PER_GROUP;
    public static final int MAX_SLOTS = MAX_QUADS + MeshBuffer.MAX_COLOURS;
    public static final int MAX_MESH_BYTES = MAX_SLOTS * ArenaSizing.QUAD_BYTES;
    public static final String MAPPED_STAGING_PROPERTY = "eminus.staging.mappedKiB";

    private static final String NAME = "eminus-arena";
    private static final int STAGING_MESHES = 4;
    private static final int STAGING_BYTES = MAX_MESH_BYTES * STAGING_MESHES;
    private static final String MAPPED_STAGING_CLASS = "com.mojang.blaze3d.vertex.StagingBuffer$PersistentlyMapped";
    private static final int RING_FILLS_PER_STAGING = 2;
    private static final int BYTES_PER_KIB = 1024;
    private static final int NOT_ASKED = 0;

    private final StagingBuffer staging;
    private final ByteBuffer scratch = ByteBuffer.allocateDirect(MAX_MESH_BYTES).order(ByteOrder.nativeOrder());
    private final LongBuffer quads = scratch.asLongBuffer();

    private ArenaUploader(StagingBuffer staging) {
        this.staging = staging;
    }

    public static ArenaUploader create() {
        RenderSystem.assertOnRenderThread();
        GpuDevice device = RenderSystem.getDevice();
        int fillKiB = Integer.getInteger(MAPPED_STAGING_PROPERTY, NOT_ASKED);
        boolean asked = fillKiB > NOT_ASKED;
        boolean mappable = device.getDeviceInfo().features().persistentMapping();
        boolean forced = asked && mappable;
        int bytes = forced ? fillKiB * BYTES_PER_KIB * RING_FILLS_PER_STAGING : STAGING_BYTES;
        StagingBuffer staging = forced ? mappedStaging(bytes) : StagingBuffer.create(NAME, device, bytes);
        if (forced) {
            Eminus.LOGGER.info("Staging buffer forced to the game's persistently mapped ring by -D{}, {} KiB per fill",
                    MAPPED_STAGING_PROPERTY, fillKiB);
        } else if (asked) {
            Eminus.LOGGER.warn("-D{} ignored: the device reports no persistent mapping", MAPPED_STAGING_PROPERTY);
        }
        return new ArenaUploader(staging);
    }

    private static StagingBuffer mappedStaging(int bytes) {
        try {
            Constructor<?> constructor = Class.forName(MAPPED_STAGING_CLASS)
                    .getDeclaredConstructor(String.class, int.class);
            constructor.setAccessible(true);
            return (StagingBuffer) constructor.newInstance(NAME, bytes);
        } catch (ReflectiveOperationException | RuntimeException refused) {
            throw new IllegalStateException("-D" + MAPPED_STAGING_PROPERTY + " could not build " + MAPPED_STAGING_CLASS,
                    refused);
        }
    }

    // One step per frame: each step rotates the game's staging ring once, and a third rotation in one submit throws.
    public Step step() {
        RenderSystem.assertOnRenderThread();
        return new Step(staging.startUploading(RenderSystem.getDevice().createCommandEncoder()));
    }

    private ByteBuffer fill(CellMesh mesh) {
        quads.clear();
        quads.put(mesh.quads(), 0, mesh.quadCount());
        for (int colour : mesh.colours()) {
            quads.put(Integer.toUnsignedLong(colour));
        }

        return scratch.clear().limit(mesh.slotCount() * ArenaSizing.QUAD_BYTES);
    }

    @Override
    public void close() {
        staging.close();
    }

    public final class Step implements AutoCloseable {
        private final StagingBuffer.Uploader uploader;
        private final List<StagingBuffer.BufferHandle> handles = new ArrayList<>();

        private Step(StagingBuffer.Uploader uploader) {
            this.uploader = uploader;
        }

        public StagingBuffer.@Nullable BufferHandle stage(CellMesh mesh) {
            StagingBuffer.BufferHandle handle = staging.tryAppend(fill(mesh));
            if (handle != null) {
                handles.add(handle);
            }

            return handle;
        }

        public void copy(StagingBuffer.BufferHandle handle, GpuBuffer target, long byteOffset) {
            uploader.copyTo(handle, target, byteOffset);
        }

        @Override
        public void close() {
            handles.forEach(StagingBuffer.BufferHandle::close);
            uploader.close();
        }
    }
}
