package com.eminus.client.settings;

import com.eminus.client.render.far.FarRenderer;
import com.eminus.client.session.ClientSession;
import com.eminus.settings.DetailDistance;
import com.eminus.settings.FarDistance;
import com.eminus.settings.LodChoice;
import com.eminus.settings.LodQuality;
import com.eminus.settings.ShaderPackLod;

import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;

public final class SettingsText {
    public static final String TITLE_KEY = "gui.eminus.settings.title";
    public static final String INGESTION_KEY = "gui.eminus.settings.ingestion";
    public static final String LOD_QUALITY_KEY = "gui.eminus.settings.lod_quality";
    public static final String FAR_RENDER_CELLS_KEY = "gui.eminus.settings.far_render_cells";
    public static final String WORKER_THREADS_KEY = "gui.eminus.settings.worker_threads";
    public static final String DETAIL_DISTANCE_KEY = "gui.eminus.settings.detail_distance";
    public static final String FOG_KEY = "gui.eminus.settings.fog";
    public static final String FADE_KEY = "gui.eminus.settings.fade";
    public static final String SHADER_PACK_LOD_KEY = "gui.eminus.settings.shader_pack_lod";
    public static final String LOD_ANIMATIONS_KEY = "gui.eminus.settings.lod_animations";
    public static final String QUALITY_CATEGORY_KEY = "gui.eminus.settings.category.quality";
    public static final String APPEARANCE_CATEGORY_KEY = "gui.eminus.settings.category.appearance";
    public static final String BUILDING_CATEGORY_KEY = "gui.eminus.settings.category.building";
    public static final String SHADER_PACKS_CATEGORY_KEY = "gui.eminus.settings.category.shader_packs";
    public static final String RESOLUTION_CATEGORY_KEY = "gui.eminus.settings.category.resolution";
    public static final String VEGETATION_CATEGORY_KEY = "gui.eminus.settings.category.vegetation";
    public static final String APPLY_CHANGES_KEY = "gui.eminus.settings.apply_changes";
    public static final String CUSTOM_LOD_KEY = "gui.eminus.settings.custom_lod";
    public static final String CUSTOM_LOD_TITLE_KEY = "gui.eminus.settings.custom_lod.title";
    public static final String NEAREST_VOXEL_KEY = "gui.eminus.settings.custom_lod.nearest_voxel";
    public static final String LEAVES_KEY = "gui.eminus.settings.custom_lod.leaves";
    public static final String GRASS_AND_FLOWERS_KEY = "gui.eminus.settings.custom_lod.grass_and_flowers";
    public static final String TEXTURES_KEY = "gui.eminus.settings.custom_lod.textures";

    private static final String HINT_SUFFIX = ".hint";
    private static final String LIMITED_SUFFIX = ".limited";
    private static final String PARAGRAPH = "\n\n";
    private static final String VANILLA_CHUNKS_KEY = "options.chunks";
    private static final String TEXTURES_FULL_SUFFIX = ".full";
    private static final String TEXTURES_HALF_SUFFIX = ".half";

    public static Component hint(String captionKey) {
        return Component.translatable(captionKey + HINT_SUFFIX);
    }

    public static <T> OptionInstance.TooltipSupplier<T> tooltip(String captionKey) {
        return OptionInstance.cachedConstantTooltip(hint(captionKey));
    }

    public static Component detailDistanceHint() {
        Component hint = hint(DETAIL_DISTANCE_KEY);
        FarRenderer renderer = ClientSession.renderer();
        if (renderer == null || !renderer.detailLimited()) {
            return hint;
        }

        return Component.empty().append(hint).append(PARAGRAPH)
                .append(Component.translatable(DETAIL_DISTANCE_KEY + LIMITED_SUFFIX));
    }

    public static Component lodChoice(LodChoice choice) {
        return Component.translatable(LOD_QUALITY_KEY + "." + choice.key());
    }

    public static Component nearestVoxel(int storedLevel) {
        return Component.translatable(NEAREST_VOXEL_KEY + "." + LodQuality.voxelBlocks(storedLevel));
    }

    public static Component textures(boolean full) {
        return Component.translatable(TEXTURES_KEY + (full ? TEXTURES_FULL_SUFFIX : TEXTURES_HALF_SUFFIX));
    }

    public static Component detailDistance(DetailDistance distance) {
        return Component.translatable(DETAIL_DISTANCE_KEY + "." + distance.key());
    }

    public static Component shaderPackLod(ShaderPackLod lod) {
        return Component.translatable(SHADER_PACK_LOD_KEY + "." + lod.key());
    }

    public static Component farRenderDistance(int cells) {
        return Component.translatable(VANILLA_CHUNKS_KEY, FarDistance.cellsToChunks(cells));
    }

    private SettingsText() {
    }
}
