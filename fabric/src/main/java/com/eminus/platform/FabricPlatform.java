package com.eminus.platform;

import java.nio.file.Path;

import com.eminus.Eminus;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatform implements Platform {
    private static final String LOADER_MOD_ID = "fabricloader";

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public String modVersion() {
        return versionOf(Eminus.MODID);
    }

    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public String loaderVersion() {
        return versionOf(LOADER_MOD_ID);
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    private static String versionOf(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
    }
}
