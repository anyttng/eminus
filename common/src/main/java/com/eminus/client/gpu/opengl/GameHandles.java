package com.eminus.client.gpu.opengl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.opengl.FrameBufferAttachment;
import com.mojang.renderpearl.backend.opengl.GlBuffer;
import com.mojang.renderpearl.backend.opengl.GlStateManager;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL30C;

final class GameHandles {
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

    static int globals() {
        GpuBuffer globals = RenderSystem.getGlobalSettingsUniform();
        if (globals == null) {
            throw new IllegalStateException("The game's globals uniform is not set");
        }
        return ((GlBuffer) globals).handle();
    }

    static boolean depthReversed() {
        return DepthStencilState.DEFAULT.depthTest() == CompareOp.GREATER_THAN_OR_EQUAL;
    }

    private static RenderTarget target() {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    }

    private static Handle handle(GpuTextureView view) {
        return new Handle(view, ((FrameBufferAttachment) view).glId());
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
        GlStateManager._colorMask(index, mask);
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
        GlStateManager._enableBlend(index);
        GlStateManager._blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
        GlStateManager._blendEquationSeparate(GL14C.GL_FUNC_ADD, GL14C.GL_FUNC_ADD);
    }

    static void noBlend(int index) {
        GlStateManager._disableBlend(index);
    }

    static void fillBothFaces() {
        GlStateManager._disableCull();
        GlStateManager._disablePolygonOffset();
        GlStateManager._polygonMode(GL11C.GL_FRONT_AND_BACK, GL11C.GL_FILL);
    }
}
