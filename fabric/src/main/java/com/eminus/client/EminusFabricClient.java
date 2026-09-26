package com.eminus.client;

import com.eminus.Eminus;
import com.eminus.client.model.game.FabricModels;
import com.eminus.client.model.game.GameModels;
import com.eminus.platform.FabricPlatform;
import com.eminus.platform.Platforms;
import com.eminus.settings.SettingsService;

import net.fabricmc.api.ClientModInitializer;

public class EminusFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Eminus.LOGGER.info("Eminus initializing");
        Platforms.set(new FabricPlatform());
        SettingsService.set(SettingsService.load(Platforms.get().configDir()));
        GameModels.useLoader(new FabricModels());
        FabricSessionHooks.register();
    }
}
