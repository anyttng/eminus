package com.eminus.client;

import com.eminus.client.session.ClientSession;
import com.eminus.ingest.IngestTrigger;
import com.eminus.settings.SettingsService;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class NeoForgeSessionHooks {
    public static void register(IEventBus gameBus) {
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> ClientSession.login());
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> ClientSession.disconnect());
        gameBus.addListener(GameShuttingDownEvent.class, event -> ClientSession.disconnect());
        gameBus.addListener(ClientTickEvent.Post.class, event -> ClientSession.tick());
        gameBus.addListener(ChunkEvent.Unload.class, NeoForgeSessionHooks::onChunkUnload);
        SettingsService.get().addListener(ClientSession::settingsChanged);
    }

    public static void registerReload(IEventBus modBus) {
        modBus.addListener(AddClientReloadListenersEvent.class, NeoForgeSessionHooks::onAddReloadListeners);
    }

    private static void onAddReloadListeners(AddClientReloadListenersEvent event) {
        ResourceManagerReloadListener reload = manager -> ClientSession.resourcesReloaded();
        event.addListener(ClientSession.RELOAD_ID, reload);
        event.addDependency(VanillaClientListeners.MODELS, ClientSession.RELOAD_ID);
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientSession.submitChunk(event.getChunk(), IngestTrigger.UNLOAD);
        }
    }

    private NeoForgeSessionHooks() {
    }
}
