package com.eminus.client;

import com.eminus.session.client.ClientSession;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class NeoForgeSessionHooks {
    public static void register(IEventBus gameBus) {
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> ClientSession.login());
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> ClientSession.disconnect());
        gameBus.addListener(GameShuttingDownEvent.class, event -> ClientSession.disconnect());
        gameBus.addListener(ClientTickEvent.Post.class, event -> ClientSession.tick());
        gameBus.addListener(ChunkEvent.Unload.class, NeoForgeSessionHooks::onChunkUnload);
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientSession.submitChunk(event.getChunk());
        }
    }

    private NeoForgeSessionHooks() {
    }
}
