package com.eminus.client.settings;

import static com.eminus.client.settings.SettingsText.FAR_RENDER_CELLS_KEY;
import static com.eminus.client.settings.SettingsText.FOG_KEY;
import static com.eminus.client.settings.SettingsText.INGESTION_KEY;
import static com.eminus.client.settings.SettingsText.LOWEST_STORED_LEVEL_KEY;
import static com.eminus.client.settings.SettingsText.SUBDIVISION_SIZE_KEY;
import static com.eminus.client.settings.SettingsText.TITLE_KEY;
import static com.eminus.client.settings.SettingsText.WORKER_THREADS_KEY;

import com.eminus.settings.Settings;
import com.eminus.settings.SettingsService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends OptionsSubScreen {
    private static final String VANILLA_PIXEL_VALUE_KEY = "options.pixel_value";

    private static final boolean APPLY_ON_RELEASE = false;

    private final OptionInstance<Boolean> ingestion;
    private final OptionInstance<Integer> lowestStoredLevel;
    private final OptionInstance<Integer> farRenderCells;
    private final OptionInstance<Integer> workerThreads;
    private final OptionInstance<Integer> subdivisionSize;
    private final OptionInstance<Boolean> fog;

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
        this.subdivisionSize = new OptionInstance<>(SUBDIVISION_SIZE_KEY, hint(SUBDIVISION_SIZE_KEY),
                (caption, value) -> Component.translatable(VANILLA_PIXEL_VALUE_KEY, caption, value),
                new OptionInstance.IntRange(Settings.MIN_SUBDIVISION_SIZE, Settings.MAX_SUBDIVISION_SIZE,
                        APPLY_ON_RELEASE),
                settings.subdivisionSize(), value -> this.apply());
        this.fog = OptionInstance.createBoolean(FOG_KEY, hint(FOG_KEY), settings.fog(), value -> this.apply());
    }

    @Override
    protected void addOptions() {
        this.list.addBig(this.ingestion);
        this.list.addBig(this.lowestStoredLevel);
        this.list.addBig(this.farRenderCells);
        this.list.addBig(this.workerThreads);
        this.list.addBig(this.subdivisionSize);
        this.list.addBig(this.fog);
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
                this.subdivisionSize.get(),
                this.fog.get()));
    }

    private static <T> OptionInstance.TooltipSupplier<T> hint(String captionKey) {
        return OptionInstance.cachedConstantTooltip(SettingsText.hint(captionKey));
    }
}
