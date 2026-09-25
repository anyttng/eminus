package com.eminus.client.gpu.opengl;

import com.eminus.gpu.Format;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.TexelView;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL31C;

final class OpenGlTexelView implements TexelView {
    private static final String LABEL_SUFFIX = "-texels";
    private static final int CREATION_UNIT = 0;

    private final OpenGlObjects objects;
    private final Buffer buffer;
    private final Format format;
    private final int texture;

    private OpenGlTexelView(OpenGlObjects objects, Buffer buffer, Format format, int texture) {
        this.objects = objects;
        this.buffer = buffer;
        this.format = format;
        this.texture = texture;
    }

    static OpenGlTexelView create(OpenGlObjects objects, String label, OpenGlBuffer buffer, Format format) {
        int texture = GameHandles.genTexture();
        GameHandles.activeTexture(CREATION_UNIT);
        GL11C.glBindTexture(GL31C.GL_TEXTURE_BUFFER, texture);
        GL31C.glTexBuffer(GL31C.GL_TEXTURE_BUFFER, OpenGlTypes.internalFormat(format), buffer.handle());
        objects.created(OpenGlObjects.Kind.TEXEL_VIEW, texture, label + LABEL_SUFFIX);
        return new OpenGlTexelView(objects, buffer, format, texture);
    }

    int texture() {
        return texture;
    }

    @Override
    public Buffer buffer() {
        return buffer;
    }

    @Override
    public Format format() {
        return format;
    }

    @Override
    public void close() {
        GameHandles.deleteTexture(texture);
        objects.deleted(OpenGlObjects.Kind.TEXEL_VIEW);
    }
}
