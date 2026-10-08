package com.eminus.client.gpu.opengl;

import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.TexelView;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pass.PassSpec;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;

import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.ARBDrawIndirect;
import org.lwjgl.opengl.ARBMultiDrawIndirect;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

final class OpenGlPass implements Pass {
    private static final int COLOUR_INDEX = 0;
    private static final int WRITE_ALL = 0b1111;
    private static final int WRITE_NONE = 0;
    private static final int FIRST_VERTEX = 0;
    private static final int TIGHTLY_PACKED = 0;
    private static final int UNBOUND = 0;
    private static final int VECTOR_COMPONENTS = 4;

    private static final int ONE_TARGET = 1;

    private final OpenGlGpu gpu;
    private final GameHandles.Bindings gameBindings;
    private final int colourTargets;
    private final int[] gameTextures = new int[Integer.SIZE];
    private @Nullable OpenGlPipeline pipeline;
    private int sampledUnits;

    private OpenGlPass(OpenGlGpu gpu, GameHandles.Bindings gameBindings, int colourTargets) {
        this.gpu = gpu;
        this.gameBindings = gameBindings;
        this.colourTargets = colourTargets;
    }

    static OpenGlPass openForeign(OpenGlGpu gpu, int framebuffer, int width, int height, int colourTargets) {
        GameHandles.Bindings gameBindings = GameHandles.bindings();
        GameHandles.bindFramebuffer(framebuffer);
        GameHandles.viewport(0, 0, width, height);
        GameHandles.scissor(0, 0, width, height);
        return new OpenGlPass(gpu, gameBindings, colourTargets);
    }

    static OpenGlPass open(OpenGlGpu gpu, PassSpec spec) {
        GameHandles.Bindings gameBindings = GameHandles.bindings();
        try {
            clear(gpu, spec);
        } catch (RuntimeException refused) {
            GameHandles.restore(gameBindings);
            throw refused;
        }
        return new OpenGlPass(gpu, gameBindings, ONE_TARGET);
    }

    private static void clear(OpenGlGpu gpu, PassSpec spec) {
        OpenGlTexture colour = (OpenGlTexture) spec.colour();
        OpenGlTexture depth = (OpenGlTexture) spec.depth();
        int framebuffer = gpu.framebuffer(colour, depth);
        GameHandles.bindFramebuffer(framebuffer);
        GameHandles.viewport(0, 0, colour.width(), colour.height());
        GameHandles.scissor(0, 0, colour.width(), colour.height());
        GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (spec.clearColour() != null) {
                GameHandles.colourMask(COLOUR_INDEX, WRITE_ALL);
                GL30C.glClearBufferfv(GL11C.GL_COLOR, COLOUR_INDEX,
                        spec.clearColour().get(stack.mallocFloat(VECTOR_COMPONENTS)));
            }

            if (depth != null && spec.clearDepth().isPresent()) {
                GameHandles.depthMask(true);
                GL30C.glClearBufferfv(GL11C.GL_DEPTH, COLOUR_INDEX,
                        stack.floats((float) spec.clearDepth().getAsDouble()));
            }
        }
    }

    @Override
    public void pipeline(Pipeline pipeline) {
        OpenGlPipeline own = (OpenGlPipeline) pipeline;
        this.pipeline = own;
        GameHandles.useProgram(own.program());
        GameHandles.bindVertexArray(gpu.vertexArray());

        PipelineSpec spec = own.spec();
        PipelineSpec.DepthTest depth = spec.depth();
        if (depth == null) {
            GameHandles.noDepthTest();
        } else {
            GameHandles.depthTest(OpenGlTypes.depthFunction(depth.compare()), depth.writes());
        }

        PipelineSpec.ColourTarget colour = spec.colour();
        for (int index = COLOUR_INDEX; index < colourTargets; index++) {
            GameHandles.colourMask(index, colour.writes() ? WRITE_ALL : WRITE_NONE);
            if (colour.blend() == null) {
                GameHandles.noBlend(index);
            } else {
                OpenGlTypes.BlendFactors factors = OpenGlTypes.blend(colour.blend());
                GameHandles.blend(index, factors.sourceRgb(), factors.destinationRgb(), factors.sourceAlpha(),
                        factors.destinationAlpha());
            }
        }
        GameHandles.fillBothFaces();
    }

    @Override
    public void bind(String name, Buffer uniform) {
        OpenGlPipeline.Slot slot = slot(name);
        if (slot != null) {
            GL30C.glBindBufferBase(GL31C.GL_UNIFORM_BUFFER, slot.index(), ((OpenGlBuffer) uniform).handle());
        }
    }

    @Override
    public void bind(String name, TexelView texels) {
        OpenGlPipeline.Slot slot = slot(name);
        if (slot != null) {
            slot.requireFormat(name, texels.format());
            GameHandles.activeTexture(slot.index());
            GL11C.glBindTexture(GL31C.GL_TEXTURE_BUFFER, ((OpenGlTexelView) texels).texture());
        }
    }

    @Override
    public void bind(String name, Texture texture, Sampler sampler) {
        OpenGlPipeline.Slot slot = slot(name);
        if (slot != null) {
            int unit = 1 << slot.index();
            int id = ((OpenGlTexture) texture).id();
            GameHandles.activeTexture(slot.index());
            if ((sampledUnits & unit) == 0) {
                gameTextures[slot.index()] = GameHandles.swapTexture(id);
            } else {
                GameHandles.bindTexture(id);
            }
            GL33C.glBindSampler(slot.index(), gpu.sampler(sampler));
            sampledUnits |= unit;
        }
    }

    private OpenGlPipeline.@Nullable Slot slot(String name) {
        if (pipeline == null) {
            throw new IllegalStateException("Binding " + name + " before a pipeline was set");
        }
        return pipeline.slot(name);
    }

    @Override
    public void quadIndices(int maxIndices) {
        gpu.bindQuadIndices(maxIndices);
    }

    @Override
    public void draw(int vertices) {
        GL11C.glDrawArrays(GL11C.GL_TRIANGLES, FIRST_VERTEX, vertices);
    }

    @Override
    public void drawIndexedIndirect(Buffer commands, int firstCommand, int count) {
        GL15C.glBindBuffer(ARBDrawIndirect.GL_DRAW_INDIRECT_BUFFER, ((OpenGlBuffer) commands).handle());
        ARBMultiDrawIndirect.glMultiDrawElementsIndirect(GL11C.GL_TRIANGLES, GL11C.GL_UNSIGNED_INT,
                (long) firstCommand * INDEXED_INDIRECT_BYTES, count, TIGHTLY_PACKED);
        GL15C.glBindBuffer(ARBDrawIndirect.GL_DRAW_INDIRECT_BUFFER, UNBOUND);
    }

    @Override
    public void close() {
        for (int units = sampledUnits; units != 0; units &= units - 1) {
            int unit = Integer.numberOfTrailingZeros(units);
            GL33C.glBindSampler(unit, UNBOUND);
            GameHandles.activeTexture(unit);
            GameHandles.bindTexture(gameTextures[unit]);
        }
        GameHandles.restore(gameBindings);
    }
}
