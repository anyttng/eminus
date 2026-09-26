package com.eminus.client.gpu.opengl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.eminus.Eminus;
import com.eminus.gpu.Capabilities;
import com.eminus.gpu.Format;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.gpu.buffer.Staging;
import com.eminus.gpu.buffer.TexelView;
import com.eminus.gpu.compute.Compute;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pass.PassSpec;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;
import com.eminus.gpu.texture.TextureUsage;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;

import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.ARBClipControl;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

public final class OpenGlGpu implements Gpu {
    public static final String BACKEND = "own OpenGL";

    private static final String VERTEX_ARRAY_LABEL = "eminus-vertex-array";
    private static final String QUAD_INDICES_LABEL = "eminus-quad-indices";
    private static final String SAMPLER_LABEL = "eminus-sampler-";
    private static final String FRAMEBUFFER_LABEL = "eminus-framebuffer-";
    private static final String TEXEL_LABEL = "eminus-texels-";
    private static final int NO_TEXTURE = 0;
    private static final int NO_BUFFER = 0;
    private static final int UNBOUND = 0;
    private static final int BASE_MIP = 0;
    private static final int NO_LOD = 0;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int INDICES_PER_QUAD = 6;
    private static final int[] QUAD_CORNERS = {0, 1, 2, 2, 3, 0};
    private static final long NO_ALLOCATION_LIMIT = Long.MAX_VALUE;
    private static final int ID_BITS = 32;

    private enum Borrowed {
        MAIN_COLOUR,
        MAIN_DEPTH,
        LIGHTMAP
    }

    private final Capabilities capabilities;
    private final OpenGlObjects objects = new OpenGlObjects();
    private final Map<Sampler, Integer> samplers = new EnumMap<>(Sampler.class);
    private final Map<Long, Integer> framebuffers = new HashMap<>();
    private final Map<Borrowed, OpenGlTexture> borrowed = new EnumMap<>(Borrowed.class);
    private final List<OpenGlPipeline> pipelines = new ArrayList<>();
    private final int vertexArray;
    private int quadIndices = NO_BUFFER;
    private int quadIndexCapacity;
    private int texelViews;

    private OpenGlGpu(Capabilities capabilities) {
        this.capabilities = capabilities;
        this.vertexArray = GL30C.glGenVertexArrays();
        int gameVertexArray = GameHandles.vertexArray();
        GameHandles.bindVertexArray(vertexArray);
        GameHandles.bindVertexArray(gameVertexArray);
        objects.created(OpenGlObjects.Kind.VERTEX_ARRAY, vertexArray, VERTEX_ARRAY_LABEL);
    }

    public static OpenGlGpu create() {
        RenderSystem.assertOnRenderThread();
        GLCapabilities gl = GL.getCapabilities();
        boolean zeroToOne = gl.GL_ARB_clip_control
                && GL11C.glGetInteger(ARBClipControl.GL_CLIP_DEPTH_MODE) == ARBClipControl.GL_ZERO_TO_ONE;

        return new OpenGlGpu(new Capabilities(BACKEND, zeroToOne, GameHandles.depthReversed(),
                GameHandles.lightmapHalfTexel(),
                gl.GL_ARB_draw_indirect, gl.GL_ARB_multi_draw_indirect, gl.GL_ARB_buffer_storage, NO_ALLOCATION_LIMIT,
                OpenGlLimits.texelElements(), OpenGlLimits.freeBytes()));
    }

    OpenGlObjects objects() {
        return objects;
    }

    int vertexArray() {
        return vertexArray;
    }

    @Override
    public Capabilities capabilities() {
        return capabilities;
    }

    @Override
    public int maxTextureSide(Format format) {
        return GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE);
    }

    @Override
    public void assertRenderThread() {
        RenderSystem.assertOnRenderThread();
    }

    @Override
    public Buffer buffer(String label, Set<BufferUsage> usage, long bytes) {
        return OpenGlBuffer.create(objects, capabilities.persistentMapping(), label, bytes);
    }

    @Override
    public Buffer buffer(String label, Set<BufferUsage> usage, ByteBuffer data) {
        return OpenGlBuffer.create(objects, capabilities.persistentMapping(), label, data);
    }

    @Override
    public void write(Buffer target, long offset, ByteBuffer data) {
        ((OpenGlBuffer) target).write(offset, data);
    }

    @Override
    public TexelView texelView(Buffer buffer, Format format) {
        return OpenGlTexelView.create(objects, TEXEL_LABEL + texelViews++, (OpenGlBuffer) buffer, format);
    }

    @Override
    public Texture texture(String label, Set<TextureUsage> usage, Format format, int width, int height, int mips) {
        return OpenGlTexture.create(this, label, format, width, height, mips);
    }

    @Override
    public void write(Texture target, int mip, int x, int y, int width, int height, ByteBuffer data) {
        ((OpenGlTexture) target).write(mip, x, y, width, height, data);
    }

    @Override
    public Texture mainColour() {
        return borrowed(Borrowed.MAIN_COLOUR, GameHandles.mainColour());
    }

    @Override
    public Texture mainDepth() {
        return borrowed(Borrowed.MAIN_DEPTH, GameHandles.mainDepth());
    }

    @Override
    public Texture lightmap() {
        return borrowed(Borrowed.LIGHTMAP, GameHandles.lightmap());
    }

    private OpenGlTexture borrowed(Borrowed slot, GameHandles.Handle handle) {
        OpenGlTexture kept = borrowed.get(slot);
        if (kept != null && kept.borrowedFrom(handle)) {
            return kept;
        }

        OpenGlTexture fresh = OpenGlTexture.borrowed(this, handle);
        if (kept != null) {
            textureClosed(kept.id());
        }
        borrowed.put(slot, fresh);
        return fresh;
    }

    @Override
    public Pipeline pipeline(PipelineSpec spec) {
        OpenGlPipeline pipeline = OpenGlPipeline.of(objects, Minecraft.getInstance().getResourceManager(), spec);
        pipelines.add(pipeline);
        return pipeline;
    }

    @Override
    public Pass pass(PassSpec spec) {
        return OpenGlPass.open(this, spec);
    }

    @Override
    public Staging staging(String label, int bytes, boolean persistentlyMapped) {
        return OpenGlStaging.create(objects, label, bytes, persistentlyMapped && capabilities.persistentMapping());
    }

    @Override
    public Optional<Compute> compute() {
        return Optional.empty();
    }

    int sampler(Sampler sampler) {
        return samplers.computeIfAbsent(sampler, this::createSampler);
    }

    private int createSampler(Sampler sampler) {
        int id = GL33C.glGenSamplers();
        GL33C.glSamplerParameteri(id, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL33C.glSamplerParameteri(id, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
        switch (sampler) {
            case NEAREST -> {
                GL33C.glSamplerParameteri(id, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
                GL33C.glSamplerParameteri(id, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
                GL33C.glSamplerParameteri(id, GL12C.GL_TEXTURE_MAX_LOD, NO_LOD);
            }
            case LINEAR -> {
                GL33C.glSamplerParameteri(id, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
                GL33C.glSamplerParameteri(id, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
                GL33C.glSamplerParameteri(id, GL12C.GL_TEXTURE_MAX_LOD, NO_LOD);
            }
            case NEAREST_MIPPED -> {
                GL33C.glSamplerParameteri(id, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST_MIPMAP_LINEAR);
                GL33C.glSamplerParameteri(id, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            }
        }
        objects.created(OpenGlObjects.Kind.SAMPLER, id, SAMPLER_LABEL + sampler.name().toLowerCase());
        return id;
    }

    int framebuffer(OpenGlTexture colour, @Nullable OpenGlTexture depth) {
        int depthId = depth == null ? NO_TEXTURE : depth.id();
        return framebuffers.computeIfAbsent(key(colour.id(), depthId), key -> createFramebuffer(colour.id(), depthId));
    }

    private int createFramebuffer(int colour, int depth) {
        int id = GL30C.glGenFramebuffers();
        GameHandles.bindFramebuffer(id);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, colour,
                BASE_MIP);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL11C.GL_TEXTURE_2D, depth,
                BASE_MIP);
        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            GameHandles.bindFramebuffer(UNBOUND);
            GameHandles.deleteFramebuffer(id);
            throw new IllegalStateException("Framebuffer of textures " + colour + " and " + depth
                    + " is incomplete: status " + status);
        }

        objects.created(OpenGlObjects.Kind.FRAMEBUFFER, id, FRAMEBUFFER_LABEL + colour + "-" + depth);
        return id;
    }

    void textureClosed(int texture) {
        Iterator<Map.Entry<Long, Integer>> entries = framebuffers.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Long, Integer> entry = entries.next();
            if (colourOf(entry.getKey()) == texture || depthOf(entry.getKey()) == texture) {
                GameHandles.deleteFramebuffer(entry.getValue());
                objects.deleted(OpenGlObjects.Kind.FRAMEBUFFER);
                entries.remove();
            }
        }
    }

    private static long key(int colour, int depth) {
        return ((long) colour << ID_BITS) | Integer.toUnsignedLong(depth);
    }

    private static int colourOf(long key) {
        return (int) (key >>> ID_BITS);
    }

    private static int depthOf(long key) {
        return (int) key;
    }

    void bindQuadIndices(int maxIndices) {
        boolean fresh = quadIndices == NO_BUFFER;
        if (fresh) {
            quadIndices = GL15C.glGenBuffers();
        }

        GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, quadIndices);
        if (fresh) {
            objects.created(OpenGlObjects.Kind.BUFFER, quadIndices, QUAD_INDICES_LABEL);
        }

        if (quadIndexCapacity < maxIndices) {
            int quads = Math.ceilDiv(maxIndices, INDICES_PER_QUAD);
            IntBuffer indices = MemoryUtil.memAllocInt(quads * INDICES_PER_QUAD);
            try {
                for (int quad = 0; quad < quads; quad++) {
                    for (int corner : QUAD_CORNERS) {
                        indices.put(quad * VERTICES_PER_QUAD + corner);
                    }
                }
                GL15C.glBufferData(GL15C.GL_ELEMENT_ARRAY_BUFFER, indices.flip(), GL15C.GL_STATIC_DRAW);
            } finally {
                MemoryUtil.memFree(indices);
            }
            quadIndexCapacity = quads * INDICES_PER_QUAD;
        }
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        pipelines.forEach(pipeline -> pipeline.delete(objects));
        pipelines.clear();
        for (int sampler : samplers.values()) {
            GL33C.glDeleteSamplers(sampler);
            objects.deleted(OpenGlObjects.Kind.SAMPLER);
        }
        samplers.clear();
        for (int framebuffer : framebuffers.values()) {
            GameHandles.deleteFramebuffer(framebuffer);
            objects.deleted(OpenGlObjects.Kind.FRAMEBUFFER);
        }
        framebuffers.clear();
        borrowed.clear();
        if (quadIndices != NO_BUFFER) {
            GL15C.glDeleteBuffers(quadIndices);
            objects.deleted(OpenGlObjects.Kind.BUFFER);
            quadIndices = NO_BUFFER;
        }
        GL30C.glDeleteVertexArrays(vertexArray);
        objects.deleted(OpenGlObjects.Kind.VERTEX_ARRAY);
        Eminus.LOGGER.info("[eminus-gl] objects live={}{}", objects.liveTotal(), objects.report());
    }
}
