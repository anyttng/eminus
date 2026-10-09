package com.eminus.api.v1;

import java.util.concurrent.CompletableFuture;

import com.eminus.client.render.far.FarRenderer;
import com.eminus.client.session.ClientSession;
import com.eminus.settings.FarDistance;
import com.eminus.settings.SettingsService;

import org.jspecify.annotations.Nullable;

/**
 * Entry point for reading the far layer on the client.
 */
public final class EminusApi {

    /**
     * Whether a far renderer runs for the dimension the client holds. Call on the render thread.
     */
    public static FarLayerStatus status() {
        if (ClientSession.renderer() != null) {
            return FarLayerStatus.RUNNING;
        }

        return ClientSession.refusal() == null ? FarLayerStatus.NO_LEVEL : FarLayerStatus.REFUSED;
    }

    /**
     * Why the far renderer was refused, as a sentence fit for a log line, or {@code null} unless {@link #status}
     * answers {@link FarLayerStatus#REFUSED}. Call on the render thread.
     */
    public static @Nullable String refusal() {
        return ClientSession.refusal();
    }

    /**
     * The far render distance the player set, in blocks of horizontal distance from the camera: the far layer draws
     * from the game's own render distance out to it. It answers the setting whether or not a far renderer runs, on
     * any thread, once mod loading has finished.
     */
    public static int farDistanceBlocks() {
        return FarDistance.cellsToBlocks(SettingsService.get().settings().farRenderCells());
    }

    /**
     * The far layer's state for the dimension the client holds, or {@code null} unless {@link #status} answers
     * {@link FarLayerStatus#RUNNING}. Call on the render thread. The tree is read on its own thread, so the future
     * completes once that thread has taken the request, and completes exceptionally when the renderer stops first.
     */
    public static @Nullable CompletableFuture<FarLayerState> farLayer() {
        FarRenderer renderer = ClientSession.renderer();
        return renderer == null ? null
                : renderer.state().thenApply(state -> new FarLayerState(state.dimension(), state.settled()));
    }

    private EminusApi() {
    }
}
