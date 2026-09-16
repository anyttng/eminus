package com.eminus.client.settings;

import com.eminus.settings.DetailDistance;
import com.eminus.settings.FarDistance;

import net.minecraft.network.chat.Component;

public final class SettingsText {
    public static final String TITLE_KEY = "gui.eminus.settings.title";
    public static final String INGESTION_KEY = "gui.eminus.settings.ingestion";
    public static final String LOWEST_STORED_LEVEL_KEY = "gui.eminus.settings.lowest_stored_level";
    public static final String FAR_RENDER_CELLS_KEY = "gui.eminus.settings.far_render_cells";
    public static final String WORKER_THREADS_KEY = "gui.eminus.settings.worker_threads";
    public static final String DETAIL_DISTANCE_KEY = "gui.eminus.settings.detail_distance";
    public static final String FOG_KEY = "gui.eminus.settings.fog";
    public static final String FADE_KEY = "gui.eminus.settings.fade";

    private static final String HINT_SUFFIX = ".hint";
    private static final String VANILLA_CHUNKS_KEY = "options.chunks";

    public static Component hint(String captionKey) {
        return Component.translatable(captionKey + HINT_SUFFIX);
    }

    public static Component lowestStoredLevel(int level) {
        return Component.translatable(LOWEST_STORED_LEVEL_KEY + "." + level);
    }

    public static Component detailDistance(DetailDistance distance) {
        return Component.translatable(DETAIL_DISTANCE_KEY + "." + distance.key());
    }

    public static Component farRenderDistance(int cells) {
        return Component.translatable(VANILLA_CHUNKS_KEY, FarDistance.cellsToChunks(cells));
    }

    private SettingsText() {
    }
}
