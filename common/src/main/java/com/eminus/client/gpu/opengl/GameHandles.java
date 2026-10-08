package com.eminus.client.gpu.opengl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.eminus.gpu.Format;
import com.eminus.gpu.Location;
import com.eminus.mixin.LightTextureAccessor;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;

final class GameHandles {
    static final int GAME_TRACKED_TEXTURE_UNITS = 12;

    private static final boolean DEPTH_REVERSED = false;
    private static final boolean LIGHTMAP_HALF_TEXEL = false;
    private static final Format COLOUR_FORMAT = Format.RGBA8_UNORM;
    private static final Format DEPTH_FORMAT = Format.D32_FLOAT;
    private static final Format LIGHTMAP_FORMAT = Format.RGBA8_UNORM;
    private static final int RED_BIT = 0b0001;
    private static final int GREEN_BIT = 0b0010;
    private static final int BLUE_BIT = 0b0100;
    private static final int ALPHA_BIT = 0b1000;
    private static final int RECTANGLE_INTS = 4;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int WIDTH = 2;
    private static final int HEIGHT = 3;
    private static final int COLOUR_COMPONENTS = 4;
    private static final int RED = 0;
    private static final int GREEN = 1;
    private static final int BLUE = 2;
    private static final int ALPHA = 3;
    private static final int POLYGON_MODE_INTS = 2;
    private static final int FRONT_FACES = 0;

    private GameHandles() {
    }

    record Handle(Object owner, int id, Format format) {
    }

    record Bindings(int drawFramebuffer, int readFramebuffer, int program, int vertexArray, int[] viewport,
            boolean scissor, int[] scissorBox, PipelineState pipeline) {
    }

    record PipelineState(boolean cull, boolean blend, int sourceRgb, int destinationRgb, int sourceAlpha,
            int destinationAlpha, int equationRgb, int equationAlpha, boolean depthTest, int depthFunction,
            boolean depthMask, int colourMask, boolean polygonOffset, int polygonMode, int activeTexture) {
    }

    private record Allocation(RenderTarget target, int generation) {
    }

    static Handle mainColour() {
        RenderTarget target = target();
        return new Handle(allocation(target), target.getColorTextureId(), COLOUR_FORMAT);
    }

    static Handle mainDepth() {
        RenderTarget target = target();
        return new Handle(allocation(target), target.getDepthTextureId(), DEPTH_FORMAT);
    }

    static Handle lightmap() {
        DynamicTexture texture =
                ((LightTextureAccessor) Minecraft.getInstance().gameRenderer.lightTexture()).eminus$texture();
        return new Handle(texture, texture.getId(), LIGHTMAP_FORMAT);
    }

    static boolean depthReversed() {
        return DEPTH_REVERSED;
    }

    static boolean lightmapHalfTexel() {
        return LIGHTMAP_HALF_TEXEL;
    }

    static Optional<String> resource(Location file) throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
                .getResource(ResourceLocation.fromNamespaceAndPath(file.namespace(), file.path()));
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream stream = resource.get().open()) {
            return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static RuntimeException outOfMemory(String message) {
        return new IllegalStateException(message);
    }

    private static RenderTarget target() {
        return Minecraft.getInstance().getMainRenderTarget();
    }

    private static Allocation allocation(RenderTarget target) {
        return new Allocation(target, ((RenderTargetGeneration) target).eminus$generation());
    }

    static Bindings bindings() {
        int[] viewport = new int[RECTANGLE_INTS];
        int[] scissorBox = new int[RECTANGLE_INTS];
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
        GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, scissorBox);
        return new Bindings(GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING), program(), vertexArray(), viewport,
                GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST), scissorBox, pipelineState());
    }

    private static PipelineState pipelineState() {
        int[] polygonMode = new int[POLYGON_MODE_INTS];
        GL11C.glGetIntegerv(GL11C.GL_POLYGON_MODE, polygonMode);
        return new PipelineState(GL11C.glIsEnabled(GL11C.GL_CULL_FACE), GL11C.glIsEnabled(GL11C.GL_BLEND),
                GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB), GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB),
                GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA), GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA),
                GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_RGB), GL11C.glGetInteger(GL20C.GL_BLEND_EQUATION_ALPHA),
                GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST), GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC),
                GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK), writtenColours(),
                GL11C.glIsEnabled(GL11C.GL_POLYGON_OFFSET_FILL), polygonMode[FRONT_FACES],
                GlStateManager._getActiveTexture());
    }

    private static int writtenColours() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer components = stack.malloc(COLOUR_COMPONENTS);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, components);
            return (components.get(RED) != GL11C.GL_FALSE ? RED_BIT : 0)
                    | (components.get(GREEN) != GL11C.GL_FALSE ? GREEN_BIT : 0)
                    | (components.get(BLUE) != GL11C.GL_FALSE ? BLUE_BIT : 0)
                    | (components.get(ALPHA) != GL11C.GL_FALSE ? ALPHA_BIT : 0);
        }
    }

    private static void restore(PipelineState state) {
        if (state.cull()) {
            GlStateManager._enableCull();
        } else {
            GlStateManager._disableCull();
        }
        if (state.blend()) {
            GlStateManager._enableBlend();
        } else {
            GlStateManager._disableBlend();
        }
        GlStateManager._blendFuncSeparate(state.sourceRgb(), state.destinationRgb(), state.sourceAlpha(),
                state.destinationAlpha());
        GL20C.glBlendEquationSeparate(state.equationRgb(), state.equationAlpha());
        if (state.depthTest()) {
            GlStateManager._enableDepthTest();
        } else {
            GlStateManager._disableDepthTest();
        }
        GlStateManager._depthFunc(state.depthFunction());
        GlStateManager._depthMask(state.depthMask());
        colourMask(state.colourMask());
        if (state.polygonOffset()) {
            GlStateManager._enablePolygonOffset();
        } else {
            GlStateManager._disablePolygonOffset();
        }
        GlStateManager._polygonMode(GL11C.GL_FRONT_AND_BACK, state.polygonMode());
        GlStateManager._activeTexture(state.activeTexture());
    }

    static void restore(Bindings bindings) {
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, bindings.drawFramebuffer());
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, bindings.readFramebuffer());
        useProgram(bindings.program());
        bindVertexArray(bindings.vertexArray());
        int[] viewport = bindings.viewport();
        GlStateManager._viewport(viewport[X], viewport[Y], viewport[WIDTH], viewport[HEIGHT]);
        int[] box = bindings.scissorBox();
        GlStateManager._scissorBox(box[X], box[Y], box[WIDTH], box[HEIGHT]);
        if (bindings.scissor()) {
            GlStateManager._enableScissorTest();
        } else {
            GlStateManager._disableScissorTest();
        }
        restore(bindings.pipeline());
    }

    static int program() {
        return GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
    }

    static void useProgram(int program) {
        GlStateManager._glUseProgram(program);
    }

    static int vertexArray() {
        return GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
    }

    static void bindVertexArray(int vertexArray) {
        GlStateManager._glBindVertexArray(vertexArray);
    }

    static int genTexture() {
        return GlStateManager._genTexture();
    }

    static void deleteTexture(int id) {
        GlStateManager._deleteTexture(id);
    }

    static void activeTexture(int unit) {
        GlStateManager._activeTexture(GL13C.GL_TEXTURE0 + unit);
    }

    static int activeTextureUnit() {
        return GlStateManager._getActiveTexture() - GL13C.GL_TEXTURE0;
    }

    static void bindTexture(int id) {
        GlStateManager._bindTexture(id);
    }

    static int swapTexture(int id) {
        int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(id);
        return previous;
    }

    static void bindFramebuffer(int framebuffer) {
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
    }

    static void bindFramebuffers(int read, int draw) {
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, draw);
    }

    static void deleteFramebuffer(int framebuffer) {
        GlStateManager._glDeleteFramebuffers(framebuffer);
    }

    static void viewport(int x, int y, int width, int height) {
        GlStateManager._viewport(x, y, width, height);
    }

    static void scissor(int x, int y, int width, int height) {
        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(x, y, width, height);
    }

    static void colourMask(int index, int mask) {
        colourMask(mask);
    }

    private static void colourMask(int mask) {
        GlStateManager._colorMask((mask & RED_BIT) != 0, (mask & GREEN_BIT) != 0, (mask & BLUE_BIT) != 0,
                (mask & ALPHA_BIT) != 0);
    }

    static void depthTest(int function, boolean writes) {
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(function);
        GlStateManager._depthMask(writes);
    }

    static void noDepthTest() {
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
    }

    static void depthMask(boolean writes) {
        GlStateManager._depthMask(writes);
    }

    static void blend(int index, int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) {
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
        GL20C.glBlendEquationSeparate(GL14C.GL_FUNC_ADD, GL14C.GL_FUNC_ADD);
    }

    static void noBlend(int index) {
        GlStateManager._disableBlend();
    }

    static void fillBothFaces() {
        GlStateManager._disableCull();
        GlStateManager._disablePolygonOffset();
        GlStateManager._polygonMode(GL11C.GL_FRONT_AND_BACK, GL11C.GL_FILL);
    }
}
