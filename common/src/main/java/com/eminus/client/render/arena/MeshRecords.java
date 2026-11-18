package com.eminus.client.render.arena;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.Set;

import com.eminus.gpu.Format;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.gpu.buffer.TexelView;
import com.eminus.mesh.QuadPieces;
import com.eminus.render.arena.ArenaAllocator;
import com.eminus.render.arena.MeshSlot;

public final class MeshRecords implements AutoCloseable {
    public static final int TEXEL_INTS = 4;
    public static final int PIECE_ENTRIES = ArenaAllocator.QUADS_PER_SEGMENT / QuadPieces.QUADS_PER_ENTRY;
    public static final int TEXELS = 1 + PIECE_ENTRIES / TEXEL_INTS;
    public static final int BYTES = TEXELS * TEXEL_INTS * Integer.BYTES;
    public static final Format TEXEL_FORMAT = Format.RGBA32_UINT;
    public static final int UNOCCLUDED_SHIFT = Integer.SIZE - 1;
    public static final int INNER_OCCLUDED_SHIFT = UNOCCLUDED_SHIFT - 1;

    private static final String LABEL = "eminus-mesh-records";
    private static final Set<BufferUsage> USAGE =
            EnumSet.of(BufferUsage.TEXEL, BufferUsage.COPY_SRC, BufferUsage.COPY_DST);
    private static final int KEY_SHIFT = 32;
    private static final int MAX_SEGMENTS_PER_MESH = ArenaAllocator.segmentsFor(ArenaUploader.MAX_SLOTS);

    private final Gpu gpu;
    private final Buffer buffer;
    private final TexelView texels;
    private final int capacity;
    private final ByteBuffer scratch =
            ByteBuffer.allocateDirect(MAX_SEGMENTS_PER_MESH * BYTES).order(ByteOrder.nativeOrder());

    private MeshRecords(Gpu gpu, Buffer buffer, int capacity) {
        this.gpu = gpu;
        this.buffer = buffer;
        this.capacity = capacity;
        texels = gpu.texelView(buffer, TEXEL_FORMAT);
    }

    public static MeshRecords create(Gpu gpu, int capacity) {
        gpu.assertRenderThread();
        return new MeshRecords(gpu, gpu.buffer(LABEL, USAGE, (long) capacity * BYTES), capacity);
    }

    public MeshRecords grown(int capacity) {
        gpu.assertRenderThread();
        MeshRecords grown = create(gpu, capacity);
        gpu.copy(buffer, 0, grown.buffer, 0, (long) this.capacity * BYTES);
        close();
        return grown;
    }

    public TexelView texels() {
        return texels;
    }

    public int capacity() {
        return capacity;
    }

    // Every segment of the mesh carries the record, so the vertex stage reads it by the quad's own segment.
    public void write(MeshSlot mesh) {
        gpu.assertRenderThread();
        int segments = ArenaAllocator.segmentsFor(mesh.slots());
        int quads = mesh.quads() | (mesh.occluded() ? 0 : 1 << UNOCCLUDED_SHIFT)
                | (mesh.innerOccluded() ? 1 << INNER_OCCLUDED_SHIFT : 0);
        scratch.clear();

        for (int segment = 0; segment < segments; segment++) {
            scratch.putInt((int) mesh.key())
                    .putInt((int) (mesh.key() >>> KEY_SHIFT))
                    .putInt(mesh.baseQuad())
                    .putInt(quads);
            for (int entry = 0; entry < PIECE_ENTRIES; entry++) {
                scratch.putInt(mesh.piecesBefore(segment * PIECE_ENTRIES + entry));
            }
        }

        scratch.flip();
        gpu.write(buffer, (long) mesh.segment() * BYTES, scratch);
    }

    @Override
    public void close() {
        texels.close();
        buffer.close();
    }
}
