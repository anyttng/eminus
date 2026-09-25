package com.eminus.client.gpu.opengl;

import java.nio.IntBuffer;
import java.util.OptionalLong;

import com.eminus.Eminus;

import org.lwjgl.opengl.ATIMeminfo;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.NVXGPUMemoryInfo;
import org.lwjgl.system.MemoryStack;

public final class OpenGlLimits {
    private static final String TEXEL_LIMIT = "texel-buffer limit";
    private static final String FREE_MEMORY = "free video memory";
    private static final long BYTES_PER_KIB = 1024L;
    private static final int ATI_MEMINFO_VALUES = 4;
    private static final int ATI_TOTAL_FREE = 0;

    private OpenGlLimits() {
    }

    public static OptionalLong texelElements() {
        return reading(TEXEL_LIMIT, () -> OptionalLong.of(GL32C.glGetInteger64(GL31C.GL_MAX_TEXTURE_BUFFER_SIZE)));
    }

    public static OptionalLong freeBytes() {
        return reading(FREE_MEMORY, OpenGlLimits::queryFreeBytes);
    }

    private static OptionalLong reading(String name, Query query) {
        try {
            return query.read();
        } catch (RuntimeException | LinkageError refused) {
            Eminus.LOGGER.warn("The {} was not read on OpenGL: {}", name, refused.toString());
            return OptionalLong.empty();
        }
    }

    private static OptionalLong queryFreeBytes() {
        GLCapabilities capabilities = GL.getCapabilities();
        if (capabilities.GL_NVX_gpu_memory_info) {
            return OptionalLong.of(GL11C.glGetInteger(NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX)
                    * BYTES_PER_KIB);
        }

        if (capabilities.GL_ATI_meminfo) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer values = stack.mallocInt(ATI_MEMINFO_VALUES);
                GL11C.glGetIntegerv(ATIMeminfo.GL_VBO_FREE_MEMORY_ATI, values);
                return OptionalLong.of(Integer.toUnsignedLong(values.get(ATI_TOTAL_FREE)) * BYTES_PER_KIB);
            }
        }

        return OptionalLong.empty();
    }

    @FunctionalInterface
    private interface Query {
        OptionalLong read();
    }
}
