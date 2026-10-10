package com.eminus.client.gpu.opengl;

import com.eminus.gpu.buffer.SparseBuffer;

import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ARBSparseBuffer;
import org.lwjgl.opengl.GL15C;

final class OpenGlSparseBuffer extends OpenGlBuffer implements SparseBuffer {
    private static final int STORAGE_FLAGS =
            ARBSparseBuffer.GL_SPARSE_STORAGE_BIT_ARB | ARBBufferStorage.GL_DYNAMIC_STORAGE_BIT;

    private final String label;

    private OpenGlSparseBuffer(OpenGlName name, long size, String label) {
        super(name, size);
        this.label = label;
    }

    static OpenGlSparseBuffer reserve(OpenGlObjects objects, String label, long bytes) {
        return new OpenGlSparseBuffer(
                allocate(objects, label, () -> ARBBufferStorage.glBufferStorage(TARGET, bytes, STORAGE_FLAGS)),
                bytes, label);
    }

    @Override
    public void commit(long offset, long bytes) {
        OpenGlErrors.clear();
        pages(offset, bytes, true);
        OpenGlErrors.check("pages of " + label);
    }

    @Override
    public void decommit(long offset, long bytes) {
        pages(offset, bytes, false);
    }

    private void pages(long offset, long bytes, boolean commit) {
        GL15C.glBindBuffer(TARGET, handle());
        ARBSparseBuffer.glBufferPageCommitmentARB(TARGET, offset, bytes, commit);
        GL15C.glBindBuffer(TARGET, UNBOUND);
    }
}
