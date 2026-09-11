package com.eminus;

import com.eminus.client.NeoForgeCommandHooks;
import com.eminus.client.NeoForgeSessionHooks;
import com.eminus.platform.NeoForgePlatform;
import com.eminus.platform.Platforms;
import com.eminus.settings.SettingsService;
import com.eminus.settings.client.SettingsScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Eminus.MODID, dist = Dist.CLIENT)
public class EminusNeoForge {
    public EminusNeoForge(ModContainer modContainer) {
        Eminus.LOGGER.info("Eminus initializing");
        Platforms.set(new NeoForgePlatform(modContainer));
        SettingsService.set(SettingsService.load(Platforms.get().configDir()));
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, modListScreen) -> new SettingsScreen(modListScreen));
        NeoForgeSessionHooks.register(NeoForge.EVENT_BUS);
        NeoForgeSessionHooks.registerReload(modContainer.getEventBus());
        NeoForgeCommandHooks.register(NeoForge.EVENT_BUS);
    }
}
