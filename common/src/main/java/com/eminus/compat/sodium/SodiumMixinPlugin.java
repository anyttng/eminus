package com.eminus.compat.sodium;

import org.slf4j.Logger;

import com.eminus.compat.ModPresence;
import com.eminus.compat.ModPresenceGatePlugin;
import com.mojang.logging.LogUtils;

public final class SodiumMixinPlugin extends ModPresenceGatePlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ModPresence SODIUM = ModPresence.gate(LOGGER, "[eminus-compat] gate sodium_present")
            .probing("net/caffeinemc/mods/sodium/client/render/chunk/lists/SectionCollector.class")
            .checking("eminus.compat.sodium.mixins.json")
            .build();

    public SodiumMixinPlugin() {
        super(SODIUM);
    }

    public static boolean sodiumPresent() {
        return SODIUM.present();
    }
}
