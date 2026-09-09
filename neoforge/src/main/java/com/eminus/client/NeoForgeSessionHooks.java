package com.eminus.client;

import com.eminus.session.client.ClientSession;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class NeoForgeSessionHooks {
    public static void register(IEventBus gameBus) {
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> ClientSession.login());
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> ClientSession.disconnect());
        gameBus.addListener(ClientTickEvent.Post.class, event -> ClientSession.tick());
    }

    private NeoForgeSessionHooks() {
    }
}
