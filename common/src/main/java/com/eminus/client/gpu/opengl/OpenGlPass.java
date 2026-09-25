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

    private final OpenGlGpu gpu;
    private @Nullable OpenGlPipeline pipeline;

    private OpenGlPass(OpenGlGpu gpu) {
        this.gpu = gpu;
    }

    static OpenGlPass open(OpenGlGpu gpu, PassSpec spec) {
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

        return new OpenGlPass(gpu);
    }

    @Override
    public void pipeline(Pipeline pipeline) {
        OpenGlPipeline own = (OpenGlPipeline) pipeline;
        this.pipeline = own;
        GL20C.glUseProgram(own.program());
        GL30C.glBindVertexArray(gpu.vertexArray());

        PipelineSpec spec = own.spec();
        PipelineSpec.DepthTest depth = spec.depth();
        if (depth == null) {
            GameHandles.noDepthTest();
        } else {
            GameHandles.depthTest(OpenGlTypes.depthFunction(depth.compare()), depth.writes());
        }

        PipelineSpec.ColourTarget colour = spec.colour();
        GameHandles.colourMask(COLOUR_INDEX, colour.writes() ? WRITE_ALL : WRITE_NONE);
        if (colour.blend() == null) {
            GameHandles.noBlend(COLOUR_INDEX);
        } else {
            OpenGlTypes.BlendFactors factors = OpenGlTypes.blend(colour.blend());
            GameHandles.blend(COLOUR_INDEX, factors.sourceRgb(), factors.destinationRgb(), factors.sourceAlpha(),
                    factors.destinationAlpha());
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
            GameHandles.activeTexture(slot.index());
            GL11C.glBindTexture(GL31C.GL_TEXTURE_BUFFER, ((OpenGlTexelView) texels).texture());
        }
    }

    @Override
    public void bind(String name, Texture texture, Sampler sampler) {
        OpenGlPipeline.Slot slot = slot(name);
        if (slot != null) {
            GameHandles.activeTexture(slot.index());
            GameHandles.bindTexture(((OpenGlTexture) texture).id());
            GL33C.glBindSampler(slot.index(), gpu.sampler(sampler));
        }
    }

    private OpenGlPipeline.@Nullable Slot slot(String name) {
        if (pipeline == null) {
            throw new IllegalStateException("Binding " + name + " before a pipeline was set");
        }
        return pipeline.slot(name);
    }

    @Override
    public void bindGameGlobals() {
        if (pipeline == null) {
            throw new IllegalStateException("Binding the game's globals before a pipeline was set");
        }

        int binding = pipeline.globalsBinding();
        if (binding != OpenGlPipeline.NOT_ACTIVE) {
            GL30C.glBindBufferBase(GL31C.GL_UNIFORM_BUFFER, binding, GameHandles.globals());
        }
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
        GL30C.glBindVertexArray(UNBOUND);
        GL20C.glUseProgram(UNBOUND);
        GameHandles.bindFramebuffer(UNBOUND);
    }
}
