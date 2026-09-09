package com.eminus;

import com.eminus.platform.NeoForgePlatform;
import com.eminus.platform.Platforms;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = Eminus.MODID, dist = Dist.CLIENT)
public class EminusNeoForge {
    public EminusNeoForge(ModContainer modContainer) {
        Eminus.LOGGER.info("Eminus initializing");
        Platforms.set(new NeoForgePlatform(modContainer));
    }
}
