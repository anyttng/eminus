package com.eminus.compat.iris;

import com.eminus.client.render.far.FarRenderer;

import net.irisshaders.iris.api.v0.IrisApi;

public final class IrisShaderPack {
    private static final boolean IRIS_PRESENT = IrisMixinPlugin.irisPresent();

    public static boolean inUse() {
        return IRIS_PRESENT && Api.inUse();
    }

    public static boolean renderingShadowPass() {
        return IRIS_PRESENT && Api.renderingShadowPass();
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
    }

    private IrisShaderPack() {
    }
}
