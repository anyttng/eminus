package com.eminus.client.settings;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.eminus.client.settings.SettingsText.CORNER_OCCLUSION_KEY;
import static com.eminus.client.settings.SettingsText.CUSTOM_LOD_TITLE_KEY;
import static com.eminus.client.settings.SettingsText.GRASS_AND_FLOWERS_KEY;
import static com.eminus.client.settings.SettingsText.LEAVES_KEY;
import static com.eminus.client.settings.SettingsText.LIGHTING_CATEGORY_KEY;
import static com.eminus.client.settings.SettingsText.NEAREST_VOXEL_KEY;
import static com.eminus.client.settings.SettingsText.RESOLUTION_CATEGORY_KEY;
import static com.eminus.client.settings.SettingsText.TEXTURES_KEY;
import static com.eminus.client.settings.SettingsText.VEGETATION_CATEGORY_KEY;
import static com.eminus.client.settings.SettingsText.tooltip;

import com.eminus.settings.CornerOcclusion;
import com.eminus.settings.LeafMode;
import com.eminus.settings.LodChoice;
import com.eminus.settings.LodQuality;
import com.eminus.settings.Settings;
import com.eminus.settings.SettingsDraft;
import com.mojang.serialization.Codec;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CustomLodScreen extends DraftOptionsScreen {
    private static final boolean APPLY_ON_RELEASE = false;

    private final OptionInstance<Integer> nearestVoxel;
    private final OptionInstance<Boolean> leaves;
    private final OptionInstance<Boolean> grassAndFlowers;
    private final OptionInstance<Boolean> textures;
    private final OptionInstance<CornerOcclusion> cornerOcclusion;

    public CustomLodScreen(Screen lastScreen, SettingsDraft draft, Consumer<Screen> back) {
        super(lastScreen, Component.translatable(CUSTOM_LOD_TITLE_KEY), draft, back);
        draft.edit(settings -> settings.withLodChoice(LodChoice.CUSTOM));

        LodQuality quality = draft.settings().customLod();

        this.nearestVoxel = new OptionInstance<>(NEAREST_VOXEL_KEY, tooltip(NEAREST_VOXEL_KEY),
                (caption, level) -> Options.genericValueLabel(caption, SettingsText.nearestVoxel(level)),
                new OptionInstance.IntRange(LodQuality.MIN_STORED_LEVEL, LodQuality.MAX_STORED_LEVEL,
                        APPLY_ON_RELEASE),
                quality.storedLevel(), level -> apply(lod -> lod.withStoredLevel(level)));
        this.leaves = OptionInstance.createBoolean(LEAVES_KEY, tooltip(SettingsText.toggleHint(LEAVES_KEY)),
                quality.leaves() == LeafMode.SEE_THROUGH,
                seeThrough -> apply(lod -> lod.withLeaves(LeafMode.of(seeThrough))));
        this.grassAndFlowers = OptionInstance.createBoolean(GRASS_AND_FLOWERS_KEY,
                tooltip(SettingsText.toggleHint(GRASS_AND_FLOWERS_KEY)), quality.drawsBlades(),
                drawn -> apply(lod -> lod.withDrawsBlades(drawn)));
        this.textures = new OptionInstance<>(TEXTURES_KEY, tooltip(SettingsText.texturesHint()),
                (caption, full) -> SettingsText.textures(full),
                OptionInstance.BOOLEAN_VALUES, quality.fullTextures(),
                full -> apply(lod -> lod.withFullTextures(full)));
        this.cornerOcclusion = new OptionInstance<>(CORNER_OCCLUSION_KEY, tooltip(SettingsText.cornerOcclusionHint()),
                (caption, value) -> SettingsText.cornerOcclusion(value),
                new OptionInstance.Enum<>(List.of(CornerOcclusion.values()),
                        Codec.STRING.xmap(key -> CornerOcclusion.fromKey(key)
                                        .orElse(Settings.DEFAULT_CUSTOM_LOD.cornerOcclusion()),
                                CornerOcclusion::key)),
                quality.cornerOcclusion(), value -> apply(lod -> lod.withCornerOcclusion(value)));
    }

    @Override
    protected void addOptions() {
        this.addHeader(RESOLUTION_CATEGORY_KEY);
        this.list.addBig(this.nearestVoxel);
        this.list.addBig(this.textures);
        this.addHeader(VEGETATION_CATEGORY_KEY);
        this.list.addBig(this.leaves);
        this.list.addBig(this.grassAndFlowers);
        this.addHeader(LIGHTING_CATEGORY_KEY);
        this.list.addBig(this.cornerOcclusion);
    }

    private void apply(UnaryOperator<LodQuality> edit) {
        this.edit(settings -> settings.withLodChoice(LodChoice.CUSTOM).withCustomLod(edit.apply(settings.customLod())));
    }
}
