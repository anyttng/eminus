package com.eminus.client.settings;

import java.util.List;

import static com.eminus.client.settings.SettingsText.DETAIL_DISTANCE_KEY;
import static com.eminus.client.settings.SettingsText.FADE_KEY;
import static com.eminus.client.settings.SettingsText.FAR_RENDER_CELLS_KEY;
import static com.eminus.client.settings.SettingsText.FOG_KEY;
import static com.eminus.client.settings.SettingsText.INGESTION_KEY;
import static com.eminus.client.settings.SettingsText.LOWEST_STORED_LEVEL_KEY;
import static com.eminus.client.settings.SettingsText.TITLE_KEY;
import static com.eminus.client.settings.SettingsText.WORKER_THREADS_KEY;

import com.eminus.settings.DetailDistance;
import com.eminus.settings.Settings;
import com.eminus.settings.SettingsService;
import com.mojang.serialization.Codec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends OptionsSubScreen {
    private static final Codec<DetailDistance> DETAIL_DISTANCE_CODEC = Codec.STRING.xmap(
            key -> DetailDistance.fromKey(key).orElse(Settings.DEFAULT_DETAIL_DISTANCE), DetailDistance::key);

    private static final boolean APPLY_ON_RELEASE = false;

    private final OptionInstance<Boolean> ingestion;
    private final OptionInstance<Integer> lowestStoredLevel;
    private final OptionInstance<Integer> farRenderCells;
    private final OptionInstance<Integer> workerThreads;
    private final OptionInstance<DetailDistance> detailDistance;
    private final OptionInstance<Boolean> fog;
    private final OptionInstance<Boolean> fade;

    public SettingsScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, Component.translatable(TITLE_KEY));

        Settings settings = SettingsService.get().settings();

        this.ingestion = OptionInstance.createBoolean(INGESTION_KEY, hint(INGESTION_KEY), settings.ingestion(),
                value -> this.apply());
        this.lowestStoredLevel = new OptionInstance<>(LOWEST_STORED_LEVEL_KEY, hint(LOWEST_STORED_LEVEL_KEY),
                (caption, value) -> Options.genericValueLabel(caption, SettingsText.lowestStoredLevel(value)),
                new OptionInstance.IntRange(Settings.MIN_DETAIL_LEVEL, Settings.MAX_DETAIL_LEVEL, APPLY_ON_RELEASE),
                settings.lowestStoredLevel(), value -> this.apply());
        this.farRenderCells = new OptionInstance<>(FAR_RENDER_CELLS_KEY, hint(FAR_RENDER_CELLS_KEY),
                (caption, value) -> Options.genericValueLabel(caption, SettingsText.farRenderDistance(value)),
                new OptionInstance.IntRange(Settings.MIN_FAR_RENDER_CELLS, Settings.MAX_FAR_RENDER_CELLS,
                        APPLY_ON_RELEASE),
                settings.farRenderCells(), value -> this.apply());
        this.workerThreads = new OptionInstance<>(WORKER_THREADS_KEY, hint(WORKER_THREADS_KEY),
                (caption, value) -> Options.genericValueLabel(caption, value),
                new OptionInstance.IntRange(Settings.MIN_WORKER_THREADS, Settings.MAX_WORKER_THREADS,
                        APPLY_ON_RELEASE),
                settings.workerThreads(), value -> this.apply());
        this.detailDistance = new OptionInstance<>(DETAIL_DISTANCE_KEY,
                value -> Tooltip.create(SettingsText.detailDistanceHint()),
                (caption, value) -> SettingsText.detailDistance(value),
                new OptionInstance.Enum<>(List.of(DetailDistance.values()), DETAIL_DISTANCE_CODEC),
                settings.detailDistance(), value -> this.apply());
        this.fog = OptionInstance.createBoolean(FOG_KEY, hint(FOG_KEY), settings.fog(), value -> this.apply());
        this.fade = OptionInstance.createBoolean(FADE_KEY, hint(FADE_KEY), settings.fade(), value -> this.apply());
    }

    @Override
    protected void addOptions() {
        this.list.addBig(this.ingestion);
        this.list.addBig(this.lowestStoredLevel);
        this.list.addBig(this.farRenderCells);
        this.list.addBig(this.workerThreads);
        this.list.addBig(this.detailDistance);
        this.list.addBig(this.fog);
        this.list.addBig(this.fade);
    }

    // Vanilla's OptionsSubScreen rewrites options.txt here, and this screen owns no vanilla option.
    @Override
    public void removed() {
    }

    private void apply() {
        SettingsService.get().update(new Settings(
                this.ingestion.get(),
                this.lowestStoredLevel.get(),
                this.farRenderCells.get(),
                this.workerThreads.get(),
                this.detailDistance.get(),
                this.fog.get(),
                this.fade.get()));
    }

    private static <T> OptionInstance.TooltipSupplier<T> hint(String captionKey) {
        return OptionInstance.cachedConstantTooltip(SettingsText.hint(captionKey));
    }
}
