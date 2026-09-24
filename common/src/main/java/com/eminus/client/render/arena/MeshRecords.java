package com.eminus.client.render.arena;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.Set;

import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.render.arena.ArenaAllocator;
import com.eminus.render.arena.MeshSlot;

public final class MeshRecords implements AutoCloseable {
    public static final int TEXELS = 1;
    public static final int BYTES = TEXELS * 4 * Integer.BYTES;

    private static final String LABEL = "eminus-mesh-records";
    private static final Set<BufferUsage> USAGE = EnumSet.of(BufferUsage.TEXEL, BufferUsage.COPY_DST);
    private static final int KEY_SHIFT = 32;
    private static final int MAX_BLOCKS_PER_MESH = ArenaAllocator.blocksFor(ArenaUploader.MAX_SLOTS);

    private final Gpu gpu;
    private final Buffer buffer;
    private final int capacity;
    private final ByteBuffer scratch =
            ByteBuffer.allocateDirect(MAX_BLOCKS_PER_MESH * BYTES).order(ByteOrder.nativeOrder());

    private MeshRecords(Gpu gpu, Buffer buffer, int capacity) {
        this.gpu = gpu;
        this.buffer = buffer;
        this.capacity = capacity;
    }

    public static MeshRecords create(Gpu gpu, int capacity) {
        gpu.assertRenderThread();
        return new MeshRecords(gpu, gpu.buffer(LABEL, USAGE, (long) capacity * BYTES), capacity);
    }

    public Buffer buffer() {
        return buffer;
    }

    public int capacity() {
        return capacity;
    }

    // Every block of the mesh carries the record, so the vertex stage reads it by the quad's own block.
    public void write(MeshSlot mesh) {
        gpu.assertRenderThread();
        int blocks = ArenaAllocator.blocksFor(mesh.slots());
        scratch.clear();

        for (int block = 0; block < blocks; block++) {
            scratch.putInt((int) mesh.key())
                    .putInt((int) (mesh.key() >>> KEY_SHIFT))
                    .putInt(mesh.baseQuad())
                    .putInt(mesh.quads());
        }

        scratch.flip();
        gpu.write(buffer, (long) mesh.block() * BYTES, scratch);
    }

    @Override
    public void close() {
        buffer.close();
    }
}
