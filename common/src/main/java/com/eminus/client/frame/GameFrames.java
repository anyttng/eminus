package com.eminus.client.frame;

import com.eminus.handoff.NearFieldOverride;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class GameFrames {
    public static final float NO_FOG = Float.MAX_VALUE;
    public static final double NO_FADE_IN = 0.0;

    private static final double MILLIS_PER_SECOND = 1000.0;

    public static GameFrame read(Minecraft client) {
        GameRenderState state = client.gameRenderer.getGameRenderState();
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        CardinalLighting shade = client.level.cardinalLighting();

        return new GameFrame(eye.x, eye.y, eye.z, camera.getViewRotationMatrix(new Matrix4f()), camera.getFov(),
                camera.getFluidInCamera() == FogType.NONE, fog(state), state.optionsRenderState.renderDistance,
                Mth.floor(sectionFadeInSeconds(client.options.chunkSectionFadeInTime().get()) * MILLIS_PER_SECOND),
                new FaceShade(shade.down(), shade.up(), shade.north(), shade.south(), shade.west(), shade.east()));
    }

    public static GameFog fog() {
        return fog(Minecraft.getInstance().gameRenderer.getGameRenderState());
    }

    public static double sectionFadeInSeconds(double option) {
        return NearFieldOverride.applied() ? NO_FADE_IN : option;
    }

    public static boolean sectionDrawn(LevelRenderer renderer, BlockPos pos, long fadeMillis) {
        return renderer.isSectionCompiledAndVisible(pos);
    }

    public static void overrideNearField(Minecraft client, boolean clearAtmosphericFog) {
        override(client.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.fogData,
                clearAtmosphericFog);
    }

    static void override(FogData fog, boolean clearAtmosphericFog) {
        fog.renderDistanceStart = NO_FOG;
        fog.renderDistanceEnd = NO_FOG;
        if (clearAtmosphericFog) {
            fog.environmentalStart = NO_FOG;
            fog.environmentalEnd = NO_FOG;
        }
    }

    private static GameFog fog(GameRenderState state) {
        FogData fog = state.levelRenderState.cameraRenderState.fogData;
        return new GameFog(fog.environmentalStart, fog.environmentalEnd, fog.renderDistanceStart,
                fog.renderDistanceEnd, new Vector4f(fog.color));
    }

    private GameFrames() {
    }
}
