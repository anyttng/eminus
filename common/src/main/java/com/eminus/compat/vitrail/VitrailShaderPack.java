package com.eminus.compat.vitrail;

import com.eminus.compat.vitrail.mixin.PackChainInvoker;

public final class VitrailShaderPack {
    private static final boolean VITRAIL_PRESENT = VitrailMixinPlugin.vitrailPresent();

    public static boolean inUse() {
        return VITRAIL_PRESENT && Chain.drawingPack();
    }

    // Holds the only reference to Vitrail's classes, so this class loads without Vitrail installed.
    private static final class Chain {
        static boolean drawingPack() {
            return PackChainInvoker.eminus$drawingPack();
        }
    }

    private VitrailShaderPack() {
    }
}
