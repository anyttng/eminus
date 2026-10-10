package com.eminus.client.gpu.opengl;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import com.eminus.client.gpu.DeviceQueries;
import com.eminus.gpu.Sparse;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.gpu.buffer.SparseBuffer;

import org.lwjgl.opengl.ARBSparseBuffer;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GLCapabilities;

final class OpenGlSparse implements Sparse {
    private static final String PAGE_SIZE = "sparse buffer page size";

    private final OpenGlObjects objects;
    private final long pageBytes;

    private OpenGlSparse(OpenGlObjects objects, long pageBytes) {
        this.objects = objects;
        this.pageBytes = pageBytes;
    }

    static Optional<Sparse> of(OpenGlObjects objects, GLCapabilities gl) {
        if (!gl.GL_ARB_sparse_buffer || !gl.GL_ARB_buffer_storage) {
            return Optional.empty();
        }

        OptionalLong page = DeviceQueries.read(DeviceQueries.OPENGL, PAGE_SIZE,
                () -> OptionalLong.of(GL11C.glGetInteger(ARBSparseBuffer.GL_SPARSE_BUFFER_PAGE_SIZE_ARB)));
        return page.isPresent() && page.getAsLong() > 0
                ? Optional.of(new OpenGlSparse(objects, page.getAsLong()))
                : Optional.empty();
    }

    @Override
    public long pageBytes() {
        return pageBytes;
    }

    @Override
    public SparseBuffer reserve(String label, Set<BufferUsage> usage, long bytes) {
        return OpenGlSparseBuffer.reserve(objects, label, bytes);
    }
}
