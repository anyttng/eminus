package com.eminus.compat.iris;

import net.irisshaders.iris.api.v0.IrisApi;

public final class IrisShaderPack {
    private static final boolean IRIS_PRESENT = IrisMixinPlugin.irisPresent();

    public static boolean inUse() {
        return IRIS_PRESENT && Api.inUse();
    }

    public static boolean renderingShadowPass() {
        return IRIS_PRESENT && Api.renderingShadowPass();
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
