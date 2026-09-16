package com.eminus.compat.sodium;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import com.eminus.Eminus;
import com.eminus.client.settings.SettingsText;
import com.eminus.settings.DetailDistance;
import com.eminus.settings.Settings;
import com.eminus.settings.SettingsService;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SodiumSettingsPage implements ConfigEntryPoint {
    private static final String INGESTION_ID = "ingestion";
    private static final String LOWEST_STORED_LEVEL_ID = "lowest_stored_level";
    private static final String FAR_RENDER_CELLS_ID = "far_render_cells";
    private static final String WORKER_THREADS_ID = "worker_threads";
    private static final String DETAIL_DISTANCE_ID = "detail_distance";
    private static final String FOG_ID = "fog";
    private static final String FADE_ID = "fade";

    private static final int STEP = 1;

    private final List<UnaryOperator<Settings>> edits = new ArrayList<>();

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        Settings defaults = Settings.defaults();

        OptionGroupBuilder group = builder.createOptionGroup()
                .addOption(builder.createBooleanOption(id(INGESTION_ID))
                        .setName(Component.translatable(SettingsText.INGESTION_KEY))
                        .setTooltip(SettingsText.hint(SettingsText.INGESTION_KEY))
                        .setStorageHandler(this::save)
                        .setDefaultValue(defaults.ingestion())
                        .setBinding(value -> edit(settings -> settings.withIngestion(value)),
                                () -> current().ingestion()))
                .addOption(builder.createIntegerOption(id(LOWEST_STORED_LEVEL_ID))
                        .setName(Component.translatable(SettingsText.LOWEST_STORED_LEVEL_KEY))
                        .setTooltip(SettingsText.hint(SettingsText.LOWEST_STORED_LEVEL_KEY))
                        .setStorageHandler(this::save)
                        .setRange(Settings.MIN_DETAIL_LEVEL, Settings.MAX_DETAIL_LEVEL, STEP)
                        .setValueFormatter(SettingsText::lowestStoredLevel)
                        .setDefaultValue(defaults.lowestStoredLevel())
                        .setBinding(value -> edit(settings -> settings.withLowestStoredLevel(value)),
                                () -> current().lowestStoredLevel()))
                .addOption(builder.createIntegerOption(id(FAR_RENDER_CELLS_ID))
                        .setName(Component.translatable(SettingsText.FAR_RENDER_CELLS_KEY))
                        .setTooltip(SettingsText.hint(SettingsText.FAR_RENDER_CELLS_KEY))
                        .setStorageHandler(this::save)
                        .setRange(Settings.MIN_FAR_RENDER_CELLS, Settings.MAX_FAR_RENDER_CELLS, STEP)
                        .setValueFormatter(SettingsText::farRenderDistance)
                        .setDefaultValue(defaults.farRenderCells())
                        .setBinding(value -> edit(settings -> settings.withFarRenderCells(value)),
                                () -> current().farRenderCells()))
                .addOption(builder.createIntegerOption(id(WORKER_THREADS_ID))
                        .setName(Component.translatable(SettingsText.WORKER_THREADS_KEY))
                        .setTooltip(SettingsText.hint(SettingsText.WORKER_THREADS_KEY))
                        .setStorageHandler(this::save)
                        .setRange(Settings.MIN_WORKER_THREADS, Settings.MAX_WORKER_THREADS, STEP)
                        .setValueFormatter(SodiumSettingsPage::number)
                        .setDefaultValue(defaults.workerThreads())
                        .setBinding(value -> edit(settings -> settings.withWorkerThreads(value)),
                                () -> current().workerThreads()))
                .addOption(builder.createEnumOption(id(DETAIL_DISTANCE_ID), DetailDistance.class)
                        .setName(Component.translatable(SettingsText.DETAIL_DISTANCE_KEY))
                        .setTooltip(SettingsText.hint(SettingsText.DETAIL_DISTANCE_KEY))
                        .setStorageHandler(this::save)
                        .setElementNameProvider(SettingsText::detailDistance)
                        .setDefaultValue(defaults.detailDistance())
                        .setBinding(value -> edit(settings -> settings.withDetailDistance(value)),
                                () -> current().detailDistance()))
                .addOption(builder.createBooleanOption(id(FOG_ID))
                        .setName(Component.translatable(SettingsText.FOG_KEY))
                        .setTooltip(SettingsText.hint(SettingsText.FOG_KEY))
                        .setStorageHandler(this::save)
                        .setDefaultValue(defaults.fog())
                        .setBinding(value -> edit(settings -> settings.withFog(value)),
                                () -> current().fog()))
                .addOption(builder.createBooleanOption(id(FADE_ID))
                        .setName(Component.translatable(SettingsText.FADE_KEY))
                        .setTooltip(SettingsText.hint(SettingsText.FADE_KEY))
                        .setStorageHandler(this::save)
                        .setDefaultValue(defaults.fade())
                        .setBinding(value -> edit(settings -> settings.withFade(value)),
                                () -> current().fade()));

        builder.registerOwnModOptions()
                .addPage(builder.createOptionPage()
                        .setName(Component.translatable(SettingsText.TITLE_KEY))
                        .addOptionGroup(group));
    }

    private void edit(UnaryOperator<Settings> edit) {
        edits.add(edit);
    }

    private void save() {
        Settings updated = current();
        for (UnaryOperator<Settings> edit : edits) {
            updated = edit.apply(updated);
        }

        edits.clear();
        SettingsService.get().update(updated);
    }

    private static Settings current() {
        return SettingsService.get().settings();
    }

    private static Component number(int value) {
        return Component.literal(String.valueOf(value));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Eminus.MODID, path);
    }
}
