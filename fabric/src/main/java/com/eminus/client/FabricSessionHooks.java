package com.eminus.client;

import com.eminus.client.session.ClientSession;
import com.eminus.settings.SettingsService;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public final class FabricSessionHooks {
    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> ClientSession.login());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (listener, client) -> client.execute(ClientSession::disconnect));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientSession.disconnect());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientSession.tick());
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> ClientSession.submitChunk(chunk));

        ResourceManagerReloadListener reload = manager -> ClientSession.resourcesReloaded();
        ResourceLoader loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        loader.registerReloadListener(ClientSession.RELOAD_ID, reload);
        loader.addListenerOrdering(ResourceReloaderKeys.Client.MODELS, ClientSession.RELOAD_ID);
        SettingsService.get().addListener(ClientSession::settingsChanged);
    }

    private FabricSessionHooks() {
    }
}
