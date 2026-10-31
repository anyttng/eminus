package com.eminus.compat.sodium.settings;

import org.slf4j.Logger;

import com.eminus.compat.ModPresence;
import com.eminus.compat.ModPresenceGatePlugin;
import com.mojang.logging.LogUtils;

public final class SodiumSettingsMixinPlugin extends ModPresenceGatePlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SODIUM_PACKAGE = "net/caffeinemc/mods/sodium/";

    private static final ModPresence SODIUM_SETTINGS = ModPresence.gate(LOGGER,
                    "[eminus/compat] gate sodium_settings_present")
            .probing("net/caffeinemc/mods/sodium/client/gui/VideoSettingsScreen.class")
            .checking("eminus.compat.sodium.settings.mixins.json")
            .modPackages(SODIUM_PACKAGE)
            .build();

    public SodiumSettingsMixinPlugin() {
        super(SODIUM_SETTINGS);
    }

    public static boolean settingsPresent() {
        return SODIUM_SETTINGS.admitted();
    }
}
