package com.eminus.client.gpu.opengl;

import com.eminus.gpu.Format;
import com.eminus.mixin.LightTextureAccessor;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

final class GameHandles {
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

    private GameHandles() {
    }

    record Handle(Object owner, int id, Format format) {
    }

    record Bindings(int drawFramebuffer, int readFramebuffer, int program, int vertexArray, int[] viewport,
            boolean scissor, int[] scissorBox) {
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
                GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST), scissorBox);
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

    static void bindTexture(int id) {
        GlStateManager._bindTexture(id);
    }

    static void bindFramebuffer(int framebuffer) {
        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
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
