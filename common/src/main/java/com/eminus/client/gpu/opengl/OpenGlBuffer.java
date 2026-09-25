package com.eminus.client.gpu.opengl;

import java.nio.ByteBuffer;

import com.eminus.gpu.buffer.Buffer;

import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL31C;

final class OpenGlBuffer implements Buffer {
    private static final int UNBOUND = 0;

    private final OpenGlObjects objects;
    private final int handle;
    private final long size;

    private OpenGlBuffer(OpenGlObjects objects, int handle, long size) {
        this.objects = objects;
        this.handle = handle;
        this.size = size;
    }

    static OpenGlBuffer create(OpenGlObjects objects, boolean immutableStorage, String label, long bytes) {
        OpenGlErrors.clear();
        int handle = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, handle);
        if (immutableStorage) {
            ARBBufferStorage.glBufferStorage(GL31C.GL_COPY_WRITE_BUFFER, bytes, ARBBufferStorage.GL_DYNAMIC_STORAGE_BIT);
        } else {
            GL15C.glBufferData(GL31C.GL_COPY_WRITE_BUFFER, bytes, GL15C.GL_DYNAMIC_DRAW);
        }
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, UNBOUND);
        OpenGlErrors.check("buffer " + label);
        objects.created(OpenGlObjects.Kind.BUFFER, handle, label);
        return new OpenGlBuffer(objects, handle, bytes);
    }

    static OpenGlBuffer create(OpenGlObjects objects, boolean immutableStorage, String label, ByteBuffer data) {
        OpenGlErrors.clear();
        int handle = GL15C.glGenBuffers();
        long bytes = data.remaining();
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, handle);
        if (immutableStorage) {
            ARBBufferStorage.glBufferStorage(GL31C.GL_COPY_WRITE_BUFFER, data, ARBBufferStorage.GL_DYNAMIC_STORAGE_BIT);
        } else {
            GL15C.glBufferData(GL31C.GL_COPY_WRITE_BUFFER, data, GL15C.GL_DYNAMIC_DRAW);
        }
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, UNBOUND);
        OpenGlErrors.check("buffer " + label);
        objects.created(OpenGlObjects.Kind.BUFFER, handle, label);
        return new OpenGlBuffer(objects, handle, bytes);
    }

    int handle() {
        return handle;
    }

    void write(long offset, ByteBuffer data) {
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, handle);
        GL15C.glBufferSubData(GL31C.GL_COPY_WRITE_BUFFER, offset, data);
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, UNBOUND);
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public void close() {
        GL15C.glDeleteBuffers(handle);
        objects.deleted(OpenGlObjects.Kind.BUFFER);
    }
}
