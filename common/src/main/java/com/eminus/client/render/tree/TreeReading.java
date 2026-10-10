package com.eminus.client.render.tree;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.eminus.client.render.far.FarRenderer;
import com.eminus.client.session.ClientSession;

import org.jspecify.annotations.Nullable;

public final class TreeReading {
    public static @Nullable CompletableFuture<List<long[]>> start(int blockX, int blockY, int blockZ) {
        FarRenderer renderer = ClientSession.renderer();
        return renderer == null ? null : renderer.describe(blockX, blockY, blockZ);
    }

    public static @Nullable CompletableFuture<Map<String, Long>> churn() {
        FarRenderer renderer = ClientSession.renderer();
        return renderer == null ? null : renderer.churn();
    }

    private TreeReading() {
    }
}
