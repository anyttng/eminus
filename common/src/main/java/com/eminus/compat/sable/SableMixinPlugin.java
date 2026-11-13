package com.eminus.compat.sable;

import org.slf4j.Logger;

import com.eminus.compat.ModPresence;
import com.eminus.compat.ModPresenceGatePlugin;
import com.mojang.logging.LogUtils;

public final class SableMixinPlugin extends ModPresenceGatePlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SABLE_PACKAGE = "dev/ryanhcode/sable/";

    private static final ModPresence SABLE = ModPresence.gate(LOGGER, "[eminus/compat] gate sable_present")
            .probing("dev/ryanhcode/sable/Sable.class")
            .checking("eminus.compat.sable.mixins.json")
            .modPackages(SABLE_PACKAGE)
            .build();

    public SableMixinPlugin() {
        super(SABLE);
    }

    public static boolean sablePresent() {
        return SABLE.admitted();
    }
}
