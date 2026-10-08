package com.eminus.compat.iris;

import java.util.Arrays;
import java.util.function.ToIntFunction;

import com.eminus.Eminus;
import com.eminus.client.render.far.FarRenderer;
import com.eminus.client.session.ClientSession;

import net.irisshaders.iris.api.v0.IrisApi;

import net.minecraft.world.level.block.state.BlockState;

import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

public final class IrisShaderPack {
    private static final boolean IRIS_PRESENT = IrisMixinPlugin.irisPresent();
    public static final String SHADOW_OFF_PROPERTY = "eminus.shadow.off";

    private static final String SHADOW_CALLBACK = "registerShadowRenderCallback";

    private static boolean listening;

    public static boolean inUse() {
        return IRIS_PRESENT && Api.inUse();
    }

    public static boolean renderingShadowPass() {
        return IRIS_PRESENT && Api.renderingShadowPass();
    }

    public static @Nullable ToIntFunction<BlockState> packIds(@Nullable ToIntFunction<BlockState> rendered) {
        return IRIS_PRESENT ? PackLayer.packIds(rendered) : null;
    }

    public static boolean contractReady(FarRenderer renderer) {
        return IRIS_PRESENT && PackLayer.readyFor(renderer);
    }

    public static void drawInPack(FarRenderer renderer, Object pipeline, boolean translucent) {
        PackLayer.draw(renderer, pipeline, translucent);
    }

    public static void pipelineDestroyed(Object pipeline) {
        PackLayer.destroyed(pipeline);
    }

    public static void listenToShadowPass() {
        if (IRIS_PRESENT && !listening) {
            listening = true;
            Api.listenToShadowPass();
        }
    }

    public static void drawShadow(FarRenderer renderer, Matrix4fc shadowView, Matrix4fc shadowProjection) {
        PackLayer.drawShadow(renderer, shadowView, shadowProjection);
    }

    public static int shadowReach(int irisChunks) {
        return PackLayer.shadowReach(irisChunks);
    }

    public static void rendererStopped() {
        if (IRIS_PRESENT) {
            PackLayer.rendererStopped();
        }
    }

    // Holds the only reference to Iris's classes, so this class loads without Iris installed.
    private static final class Api {
        static boolean inUse() {
            return IrisApi.getInstance().isShaderPackInUse();
        }

        static boolean renderingShadowPass() {
            return IrisApi.getInstance().isRenderingShadowPass();
        }

        static void listenToShadowPass() {
            if (Arrays.stream(IrisApi.class.getMethods())
                    .noneMatch(method -> method.getName().equals(SHADOW_CALLBACK))) {
                Eminus.LOGGER.info("Iris offers no shadow-pass callback: the far layer casts no shadow in a pack");
                return;
            }
            ShadowCallback.register();
        }
    }

    // Names IrisShadowRenderCallback, which an Iris without the method lacks, so it loads only after the check.
    private static final class ShadowCallback {
        static void register() {
            IrisApi.getInstance().registerShadowRenderCallback((shadowView, shadowProjection, cameraX, cameraY,
                    cameraZ, tickDelta) -> ClientSession.drawFarLayerInShadowPass(shadowView, shadowProjection));
        }
    }

    private IrisShaderPack() {
    }
}
