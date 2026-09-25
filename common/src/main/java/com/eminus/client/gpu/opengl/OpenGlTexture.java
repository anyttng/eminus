package com.eminus.client.gpu.opengl;

import java.nio.ByteBuffer;

import com.eminus.gpu.Format;
import com.eminus.gpu.texture.Texture;

import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;

final class OpenGlTexture implements Texture {
    private static final int BASE_MIP = 0;
    private static final int NO_BORDER = 0;
    private static final int PACKED_ROWS = 0;

    private final OpenGlGpu gpu;
    private final int id;
    private final Format format;
    private final int width;
    private final int height;
    private final @Nullable Object owner;

    private OpenGlTexture(OpenGlGpu gpu, int id, Format format, int width, int height, @Nullable Object owner) {
        this.gpu = gpu;
        this.id = id;
        this.format = format;
        this.width = width;
        this.height = height;
        this.owner = owner;
    }

    static OpenGlTexture create(OpenGlGpu gpu, String label, Format format, int width, int height, int mips) {
        OpenGlErrors.clear();
        int id = GameHandles.genTexture();
        GameHandles.bindTexture(id);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_BASE_LEVEL, BASE_MIP);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_MAX_LEVEL, mips - 1);
        for (int mip = 0; mip < mips; mip++) {
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, mip, OpenGlTypes.internalFormat(format), width >> mip,
                    height >> mip, NO_BORDER, OpenGlTypes.externalFormat(format), OpenGlTypes.componentType(format),
                    (ByteBuffer) null);
        }
        OpenGlErrors.check("texture " + label + " " + width + "x" + height);
        gpu.objects().created(OpenGlObjects.Kind.TEXTURE, id, label);
        return new OpenGlTexture(gpu, id, format, width, height, null);
    }

    static OpenGlTexture borrowed(OpenGlGpu gpu, GameHandles.Handle handle) {
        GameHandles.bindTexture(handle.id());
        int internalFormat = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, BASE_MIP, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
        int width = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, BASE_MIP, GL11C.GL_TEXTURE_WIDTH);
        int height = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, BASE_MIP, GL11C.GL_TEXTURE_HEIGHT);
        return new OpenGlTexture(gpu, handle.id(), OpenGlTypes.format(internalFormat), width, height, handle.owner());
    }

    int id() {
        return id;
    }

    boolean borrowedFrom(GameHandles.Handle handle) {
        return owner == handle.owner();
    }

    void write(int mip, int x, int y, int regionWidth, int regionHeight, ByteBuffer data) {
        GameHandles.bindTexture(id);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, regionWidth);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, PACKED_ROWS);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, PACKED_ROWS);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, OpenGlTypes.unpackAlignment(format));
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, mip, x, y, regionWidth, regionHeight,
                OpenGlTypes.externalFormat(format), OpenGlTypes.componentType(format), data);
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public Format format() {
        return format;
    }

    @Override
    public void close() {
        if (owner != null) {
            return;
        }

        gpu.textureClosed(id);
        GameHandles.deleteTexture(id);
        gpu.objects().deleted(OpenGlObjects.Kind.TEXTURE);
    }
}
