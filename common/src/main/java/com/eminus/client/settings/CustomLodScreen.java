package com.eminus.client.settings;

import java.util.function.UnaryOperator;

import static com.eminus.client.settings.SettingsText.CUSTOM_LOD_TITLE_KEY;
import static com.eminus.client.settings.SettingsText.GRASS_AND_FLOWERS_KEY;
import static com.eminus.client.settings.SettingsText.LEAVES_KEY;
import static com.eminus.client.settings.SettingsText.NEAREST_VOXEL_KEY;
import static com.eminus.client.settings.SettingsText.RESOLUTION_CATEGORY_KEY;
import static com.eminus.client.settings.SettingsText.TEXTURES_KEY;
import static com.eminus.client.settings.SettingsText.VEGETATION_CATEGORY_KEY;
import static com.eminus.client.settings.SettingsText.tooltip;

import com.eminus.settings.LeafMode;
import com.eminus.settings.LodChoice;
import com.eminus.settings.LodQuality;
import com.eminus.settings.SettingsDraft;

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

    public CustomLodScreen(Screen lastScreen, SettingsDraft draft, Runnable beforeReturn) {
        super(lastScreen, Component.translatable(CUSTOM_LOD_TITLE_KEY), draft, beforeReturn);
        draft.edit(settings -> settings.withLodChoice(LodChoice.CUSTOM));

        LodQuality quality = draft.settings().customLod();

        this.nearestVoxel = new OptionInstance<>(NEAREST_VOXEL_KEY, tooltip(NEAREST_VOXEL_KEY),
                (caption, level) -> Options.genericValueLabel(caption, SettingsText.nearestVoxel(level)),
                new OptionInstance.IntRange(LodQuality.MIN_STORED_LEVEL, LodQuality.MAX_STORED_LEVEL,
                        APPLY_ON_RELEASE),
                quality.storedLevel(), level -> apply(lod -> lod.withStoredLevel(level)));
        this.leaves = OptionInstance.createBoolean(LEAVES_KEY, tooltip(LEAVES_KEY),
                quality.leaves() == LeafMode.SEE_THROUGH,
                seeThrough -> apply(lod -> lod.withLeaves(LeafMode.of(seeThrough))));
        this.grassAndFlowers = OptionInstance.createBoolean(GRASS_AND_FLOWERS_KEY,
                tooltip(GRASS_AND_FLOWERS_KEY), quality.drawsBlades(),
                drawn -> apply(lod -> lod.withDrawsBlades(drawn)));
        this.textures = new OptionInstance<>(TEXTURES_KEY, tooltip(TEXTURES_KEY),
                (caption, full) -> SettingsText.textures(full),
                OptionInstance.BOOLEAN_VALUES, quality.fullTextures(),
                full -> apply(lod -> lod.withFullTextures(full)));
    }

    @Override
    protected void addOptions() {
        this.addHeader(RESOLUTION_CATEGORY_KEY);
        this.list.addBig(this.nearestVoxel);
        this.list.addBig(this.textures);
        this.addHeader(VEGETATION_CATEGORY_KEY);
        this.list.addBig(this.leaves);
        this.list.addBig(this.grassAndFlowers);
    }

    private void apply(UnaryOperator<LodQuality> edit) {
        this.edit(settings -> settings.withLodChoice(LodChoice.CUSTOM).withCustomLod(edit.apply(settings.customLod())));
    }
}
