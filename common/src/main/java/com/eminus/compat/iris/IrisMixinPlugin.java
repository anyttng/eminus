package com.eminus.compat.iris;

import org.slf4j.Logger;

import com.eminus.compat.ModPresence;
import com.eminus.compat.ModPresenceGatePlugin;
import com.mojang.logging.LogUtils;

public final class IrisMixinPlugin extends ModPresenceGatePlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ModPresence IRIS = ModPresence.gate(LOGGER, "[eminus-compat] gate iris_present")
            .probing("net/irisshaders/iris/pipeline/IrisRenderingPipeline.class")
            .checking("eminus.compat.iris.mixins.json")
            .build();

    public IrisMixinPlugin() {
        super(IRIS);
    }

    public static boolean irisPresent() {
        return IRIS.present();
    }
}
