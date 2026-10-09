package com.eminus.compat.vitrail;

import org.slf4j.Logger;

import com.eminus.compat.ModPresence;
import com.eminus.compat.ModPresenceGatePlugin;
import com.mojang.logging.LogUtils;

public final class VitrailMixinPlugin extends ModPresenceGatePlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ModPresence VITRAIL = ModPresence.gate(LOGGER, "[eminus-compat] gate vitrail_present")
            .probing("dev/vitrail/render/PackChain.class")
            .checking("eminus.compat.vitrail.mixins.json")
            .build();

    public VitrailMixinPlugin() {
        super(VITRAIL);
    }

    public static boolean vitrailPresent() {
        return VITRAIL.present();
    }
}
