package com.eminus.client.render.arena;

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
import it.unimi.dsi.fastutil.longs.LongList;

import org.jspecify.annotations.Nullable;

public final class GeometryArena implements MeshSlots, AutoCloseable {
    private static final String LABEL = "eminus-geometry-arena";
    private static final int USAGE =
            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST;
    private static final long WARNING_PERIOD_NANOS = 1_000_000_000L;
    private static final int HIGH_WATER_PERCENT = 85;
    private static final int WHOLE_PERCENT = 100;

    private final GpuBuffer quads;
    private final ArenaAllocator allocator;
    private final MeshRecords records;
    private final ArenaUploader uploader;
    private final Long2ObjectOpenHashMap<MeshSlot> held = new Long2ObjectOpenHashMap<>();

    private int refused;
    private int refusedSinceWarning;
    private int refusedQuadsMax;
    private long lastWarning = System.nanoTime() - WARNING_PERIOD_NANOS;

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

    public boolean pressure() {
        return refused > 0
                || (long) allocator.usedBlocks() * WHOLE_PERCENT >= (long) allocator.blocks() * HIGH_WATER_PERCENT;
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
                refuse(mesh.quadCount());
                continue;
            }

            uploads.add(placed);
        }

        for (ArenaUpload dropped : uploader.upload(quads, uploads)) {
            release(dropped.mesh().key());
            refuse(dropped.mesh().quadCount());
        }

        if (refused > 0) {
            warnAboutRefusals();
        }
    }

    public void evict(LongList keys) {
        RenderSystem.assertOnRenderThread();

        for (int at = 0; at < keys.size(); at++) {
            release(keys.getLong(at));
        }
    }

    private @Nullable ArenaUpload place(CellMesh mesh) {
        int block = allocator.allocate(mesh.quadCount());
        if (block == ArenaAllocator.NO_BLOCK) {
            return null;
        }

        MeshSlot placed = MeshSlot.of(mesh, block);
        held.put(mesh.key(), placed);
        records.write(placed);

        return new ArenaUpload(mesh, block, block * ArenaSizing.BLOCK_BYTES);
    }

    private void refuse(int quads) {
        refused++;
        refusedSinceWarning++;
        refusedQuadsMax = Math.max(refusedQuadsMax, quads);
    }

    private void warnAboutRefusals() {
        long now = System.nanoTime();
        if (now - lastWarning < WARNING_PERIOD_NANOS) {
            return;
        }

        lastWarning = now;
        Eminus.LOGGER.warn("{} cells of up to {} quads do not fit the arena and are dropped: {} free blocks, "
                        + "largest free run {} blocks, {} free runs",
                refusedSinceWarning, refusedQuadsMax, allocator.freeBlocks(), allocator.largestFreeRun(),
                allocator.freeRuns());
        refusedSinceWarning = 0;
        refusedQuadsMax = 0;
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
