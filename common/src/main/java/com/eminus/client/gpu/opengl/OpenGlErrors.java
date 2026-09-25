package com.eminus.client.gpu.opengl;

import com.mojang.blaze3d.GpuOutOfMemoryException;

import org.lwjgl.opengl.GL11C;

final class OpenGlErrors {
    private OpenGlErrors() {
    }

    static void clear() {
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
        }
    }

    static void check(String subject) {
        int error = GL11C.glGetError();
        if (error == GL11C.GL_OUT_OF_MEMORY) {
            throw new GpuOutOfMemoryException("OpenGL ran out of memory allocating " + subject);
        }
        if (error != GL11C.GL_NO_ERROR) {
            throw new IllegalStateException("OpenGL error " + error + " allocating " + subject);
        }
    }
}
