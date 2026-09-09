package com.eminus.settings.client;

import java.util.List;

import com.eminus.settings.FarDistance;
import com.eminus.settings.FogMode;
import com.eminus.settings.Settings;
import com.eminus.settings.SettingsService;
import com.mojang.serialization.Codec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends OptionsSubScreen {
    private static final String TITLE_KEY = "gui.eminus.settings.title";
    private static final String INGESTION_KEY = "gui.eminus.settings.ingestion";
    private static final String LOWEST_STORED_LEVEL_KEY = "gui.eminus.settings.lowest_stored_level";
    private static final String FAR_RENDER_CELLS_KEY = "gui.eminus.settings.far_render_cells";
    private static final String WORKER_THREADS_KEY = "gui.eminus.settings.worker_threads";
    private static final String SUBDIVISION_SIZE_KEY = "gui.eminus.settings.subdivision_size";
    private static final String FOG_MODE_KEY = "gui.eminus.settings.fog_mode";
    private static final String HINT_SUFFIX = ".hint";

    private static final String VANILLA_CHUNKS_KEY = "options.chunks";
    private static final String VANILLA_PIXEL_VALUE_KEY = "options.pixel_value";

    private static final Codec<FogMode> FOG_MODE_CODEC =
            Codec.STRING.xmap(key -> FogMode.fromKey(key).orElse(Settings.DEFAULT_FOG_MODE), FogMode::key);

    private static final boolean APPLY_ON_RELEASE = false;

    private final OptionInstance<Boolean> ingestion;
    private final OptionInstance<Integer> lowestStoredLevel;
    private final OptionInstance<Integer> farRenderCells;
    private final OptionInstance<Integer> workerThreads;
    private final OptionInstance<Integer> subdivisionSize;
    private final OptionInstance<FogMode> fogMode;

    public SettingsScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, Component.translatable(TITLE_KEY));

        Settings settings = SettingsService.get().settings();

        this.ingestion = OptionInstance.createBoolean(INGESTION_KEY, hint(INGESTION_KEY), settings.ingestion(),
                value -> this.apply());
        this.lowestStoredLevel = new OptionInstance<>(LOWEST_STORED_LEVEL_KEY, hint(LOWEST_STORED_LEVEL_KEY),
                (caption, value) -> Options.genericValueLabel(caption, value),
                new OptionInstance.IntRange(Settings.MIN_DETAIL_LEVEL, Settings.MAX_DETAIL_LEVEL, APPLY_ON_RELEASE),
                settings.lowestStoredLevel(), value -> this.apply());
        this.farRenderCells = new OptionInstance<>(FAR_RENDER_CELLS_KEY, hint(FAR_RENDER_CELLS_KEY),
                (caption, value) -> Options.genericValueLabel(caption,
                        Component.translatable(VANILLA_CHUNKS_KEY, FarDistance.cellsToChunks(value))),
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
        this.fogMode = new OptionInstance<>(FOG_MODE_KEY, hint(FOG_MODE_KEY),
                (caption, value) -> Options.genericValueLabel(caption, fogModeLabel(value)),
                new OptionInstance.Enum<>(List.of(FogMode.values()), FOG_MODE_CODEC),
                settings.fogMode(), value -> this.apply());
    }

    @Override
    protected void addOptions() {
        this.list.addSmall(this.ingestion, this.lowestStoredLevel);
        this.list.addSmall(this.farRenderCells, this.workerThreads);
        this.list.addSmall(this.subdivisionSize, this.fogMode);
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
                this.fogMode.get()));
    }

    private static Component fogModeLabel(FogMode mode) {
        return mode == FogMode.OFF
                ? CommonComponents.OPTION_OFF
                : Component.translatable(FOG_MODE_KEY + "." + mode.key());
    }

    private static <T> OptionInstance.TooltipSupplier<T> hint(String captionKey) {
        return OptionInstance.cachedConstantTooltip(Component.translatable(captionKey + HINT_SUFFIX));
    }
}
