package com.eminus.client;

import com.eminus.client.session.ClientSession;
import com.eminus.ingest.IngestTrigger;
import com.eminus.settings.SettingsService;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
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
        modBus.addListener(RegisterClientReloadListenersEvent.class, NeoForgeSessionHooks::onRegisterReloadListeners);
    }

    // Applies after the models: the event fires once the game has registered its own listeners, and each applies in order.
    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        ResourceManagerReloadListener reload = manager -> ClientSession.resourcesReloaded();
        event.registerReloadListener(reload);
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk chunk) {
            ClientSession.submitChunk(chunk, IngestTrigger.UNLOAD);
        }
    }

    private NeoForgeSessionHooks() {
    }
}
