package com.eminus.client;

import java.util.Collection;
import java.util.List;

import com.eminus.client.session.ClientSession;
import com.eminus.ingest.IngestTrigger;
import com.eminus.settings.SettingsService;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public final class FabricSessionHooks {
    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> ClientSession.login());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (listener, client) -> client.execute(ClientSession::disconnect));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientSession.disconnect());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientSession.tick());
        ClientChunkEvents.CHUNK_UNLOAD.register(
                (world, chunk) -> ClientSession.submitChunk(chunk, IngestTrigger.UNLOAD));

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new FarRendererReload());
        SettingsService.get().addListener(ClientSession::settingsChanged);
    }

    private FabricSessionHooks() {
    }

    private static final class FarRendererReload
            implements ResourceManagerReloadListener, IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return ClientSession.RELOAD_ID;
        }

        @Override
        public Collection<ResourceLocation> getFabricDependencies() {
            return List.of(ResourceReloadListenerKeys.MODELS);
        }

        @Override
        public void onResourceManagerReload(ResourceManager manager) {
            ClientSession.resourcesReloaded();
        }
    }
}
