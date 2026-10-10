package com.eminus.client.gpu.opengl;

import java.nio.ByteBuffer;

import com.eminus.gpu.buffer.Buffer;

import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL31C;

class OpenGlBuffer implements Buffer {
    static final int TARGET = GL31C.GL_COPY_WRITE_BUFFER;
    static final int UNBOUND = 0;

    private final OpenGlName name;
    private final long size;

    OpenGlBuffer(OpenGlName name, long size) {
        this.name = name;
        this.size = size;
    }

    static OpenGlBuffer create(OpenGlObjects objects, boolean immutableStorage, String label, long bytes) {
        return create(objects, label, bytes, immutableStorage
                ? () -> ARBBufferStorage.glBufferStorage(TARGET, bytes, ARBBufferStorage.GL_DYNAMIC_STORAGE_BIT)
                : () -> GL15C.glBufferData(TARGET, bytes, GL15C.GL_DYNAMIC_DRAW));
    }

    static OpenGlBuffer create(OpenGlObjects objects, boolean immutableStorage, String label, ByteBuffer data) {
        return create(objects, label, data.remaining(), immutableStorage
                ? () -> ARBBufferStorage.glBufferStorage(TARGET, data, ARBBufferStorage.GL_DYNAMIC_STORAGE_BIT)
                : () -> GL15C.glBufferData(TARGET, data, GL15C.GL_DYNAMIC_DRAW));
    }

    private static OpenGlBuffer create(OpenGlObjects objects, String label, long bytes, Runnable store) {
        return new OpenGlBuffer(allocate(objects, label, store), bytes);
    }

    static OpenGlName allocate(OpenGlObjects objects, String label, Runnable store) {
        OpenGlErrors.clear();
        int handle = GL15C.glGenBuffers();
        GL15C.glBindBuffer(TARGET, handle);
        store.run();
        GL15C.glBindBuffer(TARGET, UNBOUND);
        OpenGlErrors.check("buffer " + label);
        objects.created(OpenGlObjects.Kind.BUFFER, handle, label);
        return new OpenGlName(handle, id -> {
            GL15C.glDeleteBuffers(id);
            objects.deleted(OpenGlObjects.Kind.BUFFER);
        });
    }

    int handle() {
        return name.id();
    }

    void write(long offset, ByteBuffer data) {
        GL15C.glBindBuffer(TARGET, name.id());
        GL15C.glBufferSubData(TARGET, offset, data);
        GL15C.glBindBuffer(TARGET, UNBOUND);
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public void close() {
        name.delete();
    }
}
