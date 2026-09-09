package com.eminus.client;

import com.eminus.Eminus;
import com.eminus.platform.FabricPlatform;
import com.eminus.platform.Platforms;

import net.fabricmc.api.ClientModInitializer;

public class EminusFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Eminus.LOGGER.info("Eminus initializing");
        Platforms.set(new FabricPlatform());
    }
}
