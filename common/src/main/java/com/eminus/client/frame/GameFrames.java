package com.eminus.client.frame;

import com.eminus.mixin.LevelRendererAccessor;
import com.eminus.mixin.ViewAreaAccessor;

import com.mojang.blaze3d.systems.RenderSystem;

import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class GameFrames {
    public static final float NO_FOG = Float.MAX_VALUE;

    private static final boolean SHADED = true;

    private static float fov;
    private static boolean renderDistanceFog;

    public static GameFrame read(Minecraft client) {
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        ClientLevel level = client.level;

        return new GameFrame(eye.x, eye.y, eye.z,
                new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf())), fov,
                camera.getFluidInCamera() == FogType.NONE, fog(), client.options.getEffectiveRenderDistance(),
                new FaceShade(level.getShade(Direction.DOWN, SHADED), level.getShade(Direction.UP, SHADED),
                        level.getShade(Direction.NORTH, SHADED), level.getShade(Direction.SOUTH, SHADED),
                        level.getShade(Direction.WEST, SHADED), level.getShade(Direction.EAST, SHADED)));
    }

    public static void captureFov(double levelFov) {
        fov = (float) levelFov;
    }

    public static void markRenderDistanceFog(boolean marked) {
        renderDistanceFog = marked;
    }

    public static GameFog fog() {
        return fog(RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(), renderDistanceFog,
                new Vector4f(RenderSystem.getShaderFogColor()));
    }

    public static boolean cutoutLeaves() {
        return Minecraft.useFancyGraphics();
    }

    public static boolean sectionCompiled(LevelRenderer renderer, BlockPos pos) {
        return renderer.isSectionCompiled(pos);
    }

    // isSectionCompiled answers for whatever section fills the ring slot.
    public static boolean gridSectionDrawable(LevelRenderer renderer, BlockPos pos) {
        ViewArea viewArea = ((LevelRendererAccessor) renderer).eminus$viewArea();
        SectionRenderDispatcher.RenderSection section =
                viewArea == null ? null : ((ViewAreaAccessor) viewArea).eminus$renderSectionAt(pos);
        return section != null && section.getOrigin().equals(pos)
                && section.getCompiled() != SectionRenderDispatcher.CompiledSection.UNCOMPILED;
    }

    public static void visibleSections(LevelRenderer renderer, LongSet into) {
        into.clear();
        for (SectionRenderDispatcher.RenderSection section : ((LevelRendererAccessor) renderer).eminus$visibleSections()) {
            into.add(SectionPos.asLong(section.getOrigin()));
        }
    }

    public static void overrideNearField(boolean clearAtmosphericFog) {
        if (clears(renderDistanceFog, clearAtmosphericFog)) {
            RenderSystem.setShaderFogStart(NO_FOG);
            RenderSystem.setShaderFogEnd(NO_FOG);
        }
    }

    static GameFog fog(float start, float end, boolean renderDistance, Vector4fc colour) {
        return renderDistance
                ? new GameFog(NO_FOG, NO_FOG, start, end, colour)
                : new GameFog(start, end, NO_FOG, NO_FOG, colour);
    }

    static boolean clears(boolean renderDistance, boolean clearAtmosphericFog) {
        return renderDistance || clearAtmosphericFog;
    }

    private GameFrames() {
    }
}
