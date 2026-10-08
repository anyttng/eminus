package com.eminus.client.gpu.opengl;

import com.eminus.gpu.Format;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.TexelView;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL31C;

final class OpenGlTexelView implements TexelView {
    private static final String LABEL_SUFFIX = "-texels";
    private static final int CREATION_UNIT = 0;

    private final Buffer buffer;
    private final Format format;
    private final OpenGlName texture;

    private OpenGlTexelView(Buffer buffer, Format format, OpenGlName texture) {
        this.buffer = buffer;
        this.format = format;
        this.texture = texture;
    }

    static OpenGlTexelView create(OpenGlObjects objects, String label, OpenGlBuffer buffer, Format format) {
        int texture = GameHandles.genTexture();
        int previousUnit = GameHandles.activeTextureUnit();
        GameHandles.activeTexture(CREATION_UNIT);
        GL11C.glBindTexture(GL31C.GL_TEXTURE_BUFFER, texture);
        GL31C.glTexBuffer(GL31C.GL_TEXTURE_BUFFER, OpenGlTypes.internalFormat(format), buffer.handle());
        GameHandles.activeTexture(previousUnit);
        objects.created(OpenGlObjects.Kind.TEXEL_VIEW, texture, label + LABEL_SUFFIX);
        return new OpenGlTexelView(buffer, format, new OpenGlName(texture, id -> {
            GameHandles.deleteTexture(id);
            objects.deleted(OpenGlObjects.Kind.TEXEL_VIEW);
        }));
    }

    int texture() {
        return texture.id();
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
        texture.delete();
    }
}
