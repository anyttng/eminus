package com.eminus.client;

import com.eminus.session.client.ClientSession;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class FabricSessionHooks {
    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> ClientSession.login());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (listener, client) -> client.execute(ClientSession::disconnect));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientSession.disconnect());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientSession.tick());
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> ClientSession.submitChunk(chunk));
    }

    private FabricSessionHooks() {
    }
}
