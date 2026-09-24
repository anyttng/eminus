package com.eminus.client.render.arena;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.eminus.Eminus;
import com.eminus.api.v1.ArenaState;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.gpu.buffer.Staging;
import com.eminus.mesh.CellMesh;
import com.eminus.render.arena.ArenaAllocator;
import com.eminus.render.arena.ArenaPressure;
import com.eminus.render.arena.ArenaSizing;
import com.eminus.render.arena.MeshSlot;
import com.eminus.render.arena.MeshSlots;
import com.eminus.render.backend.BackendSupport;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;

import org.jspecify.annotations.Nullable;

public final class GeometryArena implements MeshSlots, AutoCloseable {
    private static final String LABEL = "eminus-geometry-arena";
    private static final Set<BufferUsage> USAGE =
            EnumSet.of(BufferUsage.VERTEX, BufferUsage.TEXEL, BufferUsage.COPY_DST);
    private static final long WARNING_PERIOD_NANOS = 1_000_000_000L;
    private static final int MIB_SHIFT = 20;

    private final Gpu gpu;
    private final Buffer quads;
    private final long bytes;
    private final ArenaAllocator allocator;
    private final MeshRecords records;
    private final ArenaUploader uploader;
    private final Long2ObjectOpenHashMap<MeshSlot> held = new Long2ObjectOpenHashMap<>();
    private final ArenaPressure pressure = new ArenaPressure();

    private int refused;
    private long refusedTotal;
    private int refusedSinceWarning;
    private int refusedQuadsMax;
    private long lastWarning = System.nanoTime() - WARNING_PERIOD_NANOS;
    private boolean announcedPressure;

    private GeometryArena(Gpu gpu, Buffer quads, long bytes, ArenaAllocator allocator, MeshRecords records,
            ArenaUploader uploader) {
        this.gpu = gpu;
        this.quads = quads;
        this.bytes = bytes;
        this.allocator = allocator;
        this.records = records;
        this.uploader = uploader;
    }

    public static @Nullable GeometryArena create(Gpu gpu, BackendSupport support, long bytes) {
        gpu.assertRenderThread();

        if (!support.accepted()) {
            return null;
        }

        int blocks = ArenaSizing.blocks(bytes);
        Buffer quads = gpu.buffer(LABEL, USAGE, bytes);
        Eminus.LOGGER.info("Geometry arena of {} MiB: {} blocks of {} quads",
                bytes >> MIB_SHIFT, blocks, ArenaAllocator.QUADS_PER_BLOCK);

        return new GeometryArena(gpu, quads, bytes, new ArenaAllocator(blocks), MeshRecords.create(gpu, blocks),
                ArenaUploader.create(gpu));
    }

    public Buffer quads() {
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

    public ArenaState state() {
        return new ArenaState(bytes, allocator.blocks(), held.size(), allocator.usedBlocks(), allocator.freeBlocks(),
                allocator.largestFreeRun(), allocator.freeRuns(), refusedTotal, pressure());
    }

    public boolean pressure() {
        return pressure.holding();
    }

    @Override
    public @Nullable MeshSlot slot(long key) {
        return held.get(key);
    }

    public int accept(List<CellMesh> meshes, int from) {
        gpu.assertRenderThread();
        refused = 0;
        int index = from;

        try (ArenaUploader.Step step = uploader.step()) {
            while (index < meshes.size()) {
                CellMesh mesh = meshes.get(index);
                if (mesh.isEmpty()) {
                    release(mesh.key());
                    index++;
                    continue;
                }

                Staging.Staged staged = step.stage(mesh);
                if (staged == null) {
                    break;
                }

                release(mesh.key());
                MeshSlot placed = place(mesh);
                if (placed == null) {
                    refuse(mesh.quadCount());
                } else {
                    step.copy(staged, quads, placed.block() * ArenaSizing.BLOCK_BYTES);
                }

                index++;
            }
        }

        if (refused > 0) {
            warnAboutRefusals();
        }

        if (pressure.update(allocator.usedBlocks(), allocator.blocks(), refused > 0) && !announcedPressure) {
            announcedPressure = true;
            Eminus.LOGGER.info("Geometry arena of {} MiB is full: the far layer stops refining and is drawn coarser"
                    + " than the detail distance asks", bytes >> MIB_SHIFT);
        }

        return index;
    }

    public void evict(LongList keys) {
        gpu.assertRenderThread();

        for (int at = 0; at < keys.size(); at++) {
            release(keys.getLong(at));
        }
    }

    private @Nullable MeshSlot place(CellMesh mesh) {
        int block = allocator.allocate(mesh.slotCount());
        if (block == ArenaAllocator.NO_BLOCK) {
            return null;
        }

        MeshSlot placed = MeshSlot.of(mesh, block);
        held.put(mesh.key(), placed);
        records.write(placed);

        return placed;
    }

    private void refuse(int quads) {
        refused++;
        refusedTotal++;
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
            allocator.free(released.block(), released.slots());
        }
    }

    @Override
    public void close() {
        uploader.close();
        records.close();
        quads.close();
    }
}
