package com.eminus.render.arena.client;

import java.util.ArrayList;
import java.util.List;

import com.eminus.Eminus;
import com.eminus.mesh.CellMesh;
import com.eminus.render.arena.ArenaAllocator;
import com.eminus.render.arena.ArenaSizing;
import com.eminus.render.arena.MeshSlot;
import com.eminus.render.arena.MeshSlots;
import com.eminus.render.backend.BackendSupport;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import org.jspecify.annotations.Nullable;

public final class GeometryArena implements MeshSlots, AutoCloseable {
    private static final String LABEL = "eminus-geometry-arena";
    private static final int USAGE =
            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST;

    private final GpuBuffer quads;
    private final ArenaAllocator allocator;
    private final MeshRecords records;
    private final ArenaUploader uploader;
    private final Long2ObjectOpenHashMap<MeshSlot> held = new Long2ObjectOpenHashMap<>();

    private int refused;

    private GeometryArena(GpuBuffer quads, ArenaAllocator allocator, MeshRecords records, ArenaUploader uploader) {
        this.quads = quads;
        this.allocator = allocator;
        this.records = records;
        this.uploader = uploader;
    }

    public static @Nullable GeometryArena create(BackendSupport support, long bytes) {
        RenderSystem.assertOnRenderThread();

        if (!support.accepted()) {
            return null;
        }

        int blocks = ArenaSizing.blocks(bytes);
        GpuBuffer quads = RenderSystem.getDevice().createBuffer(() -> LABEL, USAGE, bytes);
        Eminus.LOGGER.info("Geometry arena of {} MiB: {} blocks of {} quads",
                bytes >> 20, blocks, ArenaAllocator.QUADS_PER_BLOCK);

        return new GeometryArena(quads, new ArenaAllocator(blocks), MeshRecords.create(blocks), ArenaUploader.create());
    }

    public GpuBuffer quads() {
        return quads;
    }

    public MeshRecords records() {
        return records;
    }

    public int meshes() {
        return held.size();
    }

    public int usedBlocks() {
        return allocator.usedBlocks();
    }

    public int refused() {
        return refused;
    }

    public int uploaded() {
        return uploader.uploaded();
    }

    @Override
    public @Nullable MeshSlot slot(long key) {
        return held.get(key);
    }

    public void accept(List<CellMesh> meshes) {
        RenderSystem.assertOnRenderThread();
        List<ArenaUpload> uploads = new ArrayList<>(meshes.size());
        refused = 0;

        for (CellMesh mesh : meshes) {
            release(mesh.key());

            if (mesh.isEmpty()) {
                continue;
            }

            ArenaUpload placed = place(mesh);
            if (placed == null) {
                refused++;
                continue;
            }

            uploads.add(placed);
        }

        for (ArenaUpload dropped : uploader.upload(quads, uploads)) {
            release(dropped.mesh().key());
            refused++;
        }
    }

    private @Nullable ArenaUpload place(CellMesh mesh) {
        int block = allocator.allocate(mesh.quadCount());
        if (block == ArenaAllocator.NO_BLOCK) {
            Eminus.LOGGER.warn("Cell {} of {} quads does not fit the {} free blocks of the arena and is dropped",
                    mesh.key(), mesh.quadCount(), allocator.freeBlocks());
            return null;
        }

        MeshSlot placed = MeshSlot.of(mesh, block);
        held.put(mesh.key(), placed);
        records.write(placed);

        return new ArenaUpload(mesh, block, block * ArenaSizing.BLOCK_BYTES);
    }

    private void release(long key) {
        MeshSlot released = held.remove(key);
        if (released != null) {
            allocator.free(released.block(), released.quads());
        }
    }

    @Override
    public void close() {
        uploader.close();
        records.close();
        quads.close();
    }
}
