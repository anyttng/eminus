package com.eminus.api.v1;

import java.util.concurrent.CompletableFuture;

import com.eminus.client.render.far.FarRenderer;
import com.eminus.client.session.ClientSession;

import org.jspecify.annotations.Nullable;

/**
 * Entry point for reading the far layer on the client.
 */
public final class EminusApi {

    /**
     * The far layer's state for the dimension the client holds, or {@code null} when no far renderer runs — before
     * login, between dimensions, or when the render backend refused it. Call on the render thread. The arena and the
     * ingest counts are read at the call; the tree is read on its own thread, so the future completes once that
     * thread has taken the request, and completes exceptionally when the renderer stops first.
     */
    public static @Nullable CompletableFuture<FarLayerState> farLayer() {
        FarRenderer renderer = ClientSession.renderer();
        return renderer == null ? null : renderer.state();
    }

    private EminusApi() {
    }
}
