package com.eminus.client.render.arena;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;

import com.eminus.Eminus;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.Staging;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.MeshBuffer;
import com.eminus.render.arena.ArenaSizing;

import org.jspecify.annotations.Nullable;

public final class ArenaUploader implements AutoCloseable {
    public static final int MAX_QUADS = MeshBuffer.MAX_QUADS;
    public static final int MAX_SLOTS = MAX_QUADS + MeshBuffer.MAX_COLOURS;
    public static final int MAX_MESH_BYTES = MAX_SLOTS * ArenaSizing.QUAD_BYTES;
    public static final String MAPPED_STAGING_PROPERTY = "eminus.staging.mappedKiB";

    private static final String NAME = "eminus-arena";
    private static final int STAGING_MESHES = 4;
    private static final int STAGING_BYTES = MAX_MESH_BYTES * STAGING_MESHES;
    private static final int RING_FILLS_PER_STAGING = 2;
    private static final int BYTES_PER_KIB = 1024;
    private static final int NOT_ASKED = 0;

    private final Gpu gpu;
    private final Staging staging;
    private final ByteBuffer scratch = ByteBuffer.allocateDirect(MAX_MESH_BYTES).order(ByteOrder.nativeOrder());
    private final LongBuffer quads = scratch.asLongBuffer();

    private ArenaUploader(Gpu gpu, Staging staging) {
        this.gpu = gpu;
        this.staging = staging;
    }

    public static ArenaUploader create(Gpu gpu) {
        gpu.assertRenderThread();
        int fillKiB = Integer.getInteger(MAPPED_STAGING_PROPERTY, NOT_ASKED);
        boolean asked = fillKiB > NOT_ASKED;
        boolean forced = asked && gpu.capabilities().persistentMapping();
        int bytes = forced ? fillKiB * BYTES_PER_KIB * RING_FILLS_PER_STAGING : STAGING_BYTES;
        Staging staging = gpu.staging(NAME, bytes, forced);
        if (forced) {
            Eminus.LOGGER.info("Staging buffer forced to the game's persistently mapped ring by -D{}, {} KiB per fill",
                    MAPPED_STAGING_PROPERTY, fillKiB);
        } else if (asked) {
            Eminus.LOGGER.warn("-D{} ignored: the device reports no persistent mapping", MAPPED_STAGING_PROPERTY);
        }
        return new ArenaUploader(gpu, staging);
    }

    // One step per frame: each step rotates the game's staging ring once, and a third rotation in one submit throws.
    public Step step() {
        gpu.assertRenderThread();
        return new Step(staging.step());
    }

    private ByteBuffer fill(CellMesh mesh) {
        quads.clear();
        quads.put(mesh.quads(), 0, mesh.quadCount());
        quads.put(mesh.colours());

        return scratch.clear().limit(mesh.slotCount() * ArenaSizing.QUAD_BYTES);
    }

    @Override
    public void close() {
        staging.close();
    }

    public final class Step implements AutoCloseable {
        private final Staging.Step step;

        private Step(Staging.Step step) {
            this.step = step;
        }

        public Staging.@Nullable Staged stage(CellMesh mesh) {
            return step.stage(fill(mesh));
        }

        public void copy(Staging.Staged staged, Buffer target, long byteOffset) {
            step.copy(staged, target, byteOffset);
        }

        @Override
        public void close() {
            step.close();
        }
    }
}
