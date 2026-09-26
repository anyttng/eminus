package com.eminus.client.gpu.opengl;

import com.eminus.gpu.Format;
import com.eminus.gpu.pipeline.Blend;
import com.eminus.gpu.pipeline.DepthCompare;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL30C;

final class OpenGlTypes {
    private static final int BYTE_COMPONENT = 1;
    private static final int WORD_COMPONENT = 4;

    private OpenGlTypes() {
    }

    record BlendFactors(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) {
    }

    static int internalFormat(Format format) {
        return switch (format) {
            case RGBA8_UNORM -> GL11C.GL_RGBA8;
            case R8_UNORM -> GL30C.GL_R8;
            case D32_FLOAT -> GL30C.GL_DEPTH_COMPONENT32F;
            case D32_UNORM -> GL14C.GL_DEPTH_COMPONENT32;
            case R32_UINT -> GL30C.GL_R32UI;
            case RG32_UINT -> GL30C.GL_RG32UI;
            case RGBA32_UINT -> GL30C.GL_RGBA32UI;
            case RGBA32_FLOAT -> GL30C.GL_RGBA32F;
        };
    }

    static Format format(int internalFormat) {
        for (Format candidate : Format.values()) {
            if (internalFormat(candidate) == internalFormat) {
                return candidate;
            }
        }
        throw new IllegalStateException("OpenGL internal format " + internalFormat + " has no port format");
    }

    static int externalFormat(Format format) {
        return switch (format) {
            case RGBA8_UNORM, RGBA32_FLOAT -> GL11C.GL_RGBA;
            case R8_UNORM -> GL11C.GL_RED;
            case D32_FLOAT, D32_UNORM -> GL11C.GL_DEPTH_COMPONENT;
            case R32_UINT -> GL30C.GL_RED_INTEGER;
            case RG32_UINT -> GL30C.GL_RG_INTEGER;
            case RGBA32_UINT -> GL30C.GL_RGBA_INTEGER;
        };
    }

    static int componentType(Format format) {
        return switch (format) {
            case RGBA8_UNORM, R8_UNORM -> GL11C.GL_UNSIGNED_BYTE;
            case R32_UINT, RG32_UINT, RGBA32_UINT, D32_UNORM -> GL11C.GL_UNSIGNED_INT;
            case D32_FLOAT, RGBA32_FLOAT -> GL11C.GL_FLOAT;
        };
    }

    static int unpackAlignment(Format format) {
        return switch (format) {
            case RGBA8_UNORM, R8_UNORM -> BYTE_COMPONENT;
            case R32_UINT, RG32_UINT, RGBA32_UINT, D32_FLOAT, D32_UNORM, RGBA32_FLOAT -> WORD_COMPONENT;
        };
    }

    static int depthFunction(DepthCompare compare) {
        return switch (compare) {
            case GREATER_OR_EQUAL -> GL11C.GL_GEQUAL;
            case LESS_OR_EQUAL -> GL11C.GL_LEQUAL;
        };
    }

    static BlendFactors blend(Blend blend) {
        return switch (blend) {
            case TRANSLUCENT -> new BlendFactors(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA, GL11C.GL_ONE,
                    GL11C.GL_ONE_MINUS_SRC_ALPHA);
            case TRANSLUCENT_PREMULTIPLIED -> new BlendFactors(GL11C.GL_ONE, GL11C.GL_ONE_MINUS_SRC_ALPHA,
                    GL11C.GL_ONE, GL11C.GL_ONE_MINUS_SRC_ALPHA);
            case MULTIPLY -> new BlendFactors(GL11C.GL_ZERO, GL11C.GL_SRC_COLOR, GL11C.GL_ZERO, GL11C.GL_ONE);
        };
    }
}
