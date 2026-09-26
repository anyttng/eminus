package com.eminus.client.gpu.opengl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTextureView;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

final class GameHandles {
    private static final boolean LIGHTMAP_HALF_TEXEL = true;

    private GameHandles() {
    }

    record Handle(Object owner, int id) {
    }

    static Handle mainColour() {
        return handle(target().getColorTextureView());
    }

    static Handle mainDepth() {
        return handle(target().getDepthTextureView());
    }

    static Handle lightmap() {
        return handle(Minecraft.getInstance().gameRenderer.lightmap());
    }

    static boolean depthReversed() {
        return DepthStencilState.DEFAULT.depthTest() == CompareOp.GREATER_THAN_OR_EQUAL;
    }

    static boolean lightmapHalfTexel() {
        return LIGHTMAP_HALF_TEXEL;
    }

    private static RenderTarget target() {
        return Minecraft.getInstance().getMainRenderTarget();
    }

    private static Handle handle(GpuTextureView view) {
        return new Handle(view, ((GlTextureView) view).texture().glId());
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
        GlStateManager._colorMask(mask);
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
