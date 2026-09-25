package com.eminus.client.gpu.opengl;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.KHRDebug;

final class OpenGlObjects {
    private static final String BUFFER_KEY = "buffers";
    private static final String TEXTURE_KEY = "textures";
    private static final String TEXEL_VIEW_KEY = "texel_views";
    private static final String PROGRAM_KEY = "programs";
    private static final String FRAMEBUFFER_KEY = "framebuffers";
    private static final String SAMPLER_KEY = "samplers";
    private static final String VERTEX_ARRAY_KEY = "vertex_arrays";

    enum Kind {
        BUFFER(KHRDebug.GL_BUFFER, BUFFER_KEY),
        TEXTURE(GL11C.GL_TEXTURE, TEXTURE_KEY),
        TEXEL_VIEW(GL11C.GL_TEXTURE, TEXEL_VIEW_KEY),
        PROGRAM(KHRDebug.GL_PROGRAM, PROGRAM_KEY),
        FRAMEBUFFER(GL30C.GL_FRAMEBUFFER, FRAMEBUFFER_KEY),
        SAMPLER(KHRDebug.GL_SAMPLER, SAMPLER_KEY),
        VERTEX_ARRAY(GL11.GL_VERTEX_ARRAY, VERTEX_ARRAY_KEY);

        private final int labelTarget;
        private final String key;

        Kind(int labelTarget, String key) {
            this.labelTarget = labelTarget;
            this.key = key;
        }
    }

    private final int[] live = new int[Kind.values().length];
    private final boolean labels = GL.getCapabilities().GL_KHR_debug;

    void created(Kind kind, int id, String label) {
        live[kind.ordinal()]++;
        if (labels) {
            KHRDebug.glObjectLabel(kind.labelTarget, id, label);
        }
    }

    void deleted(Kind kind) {
        live[kind.ordinal()]--;
    }

    int liveTotal() {
        int total = 0;
        for (int count : live) {
            total += count;
        }
        return total;
    }

    String report() {
        StringBuilder report = new StringBuilder();
        for (Kind kind : Kind.values()) {
            report.append(' ').append(kind.key).append('=').append(live[kind.ordinal()]);
        }
        return report.toString();
    }
}
