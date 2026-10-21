package com.eminus.client.settings;

import java.util.List;

import static com.eminus.client.settings.SettingsText.APPEARANCE_CATEGORY_KEY;
import static com.eminus.client.settings.SettingsText.BUILDING_CATEGORY_KEY;
import static com.eminus.client.settings.SettingsText.CUSTOM_LOD_KEY;
import static com.eminus.client.settings.SettingsText.DETAIL_DISTANCE_KEY;
import static com.eminus.client.settings.SettingsText.FADE_KEY;
import static com.eminus.client.settings.SettingsText.FAR_RENDER_CELLS_KEY;
import static com.eminus.client.settings.SettingsText.FOG_KEY;
import static com.eminus.client.settings.SettingsText.GENERAL_CATEGORY_KEY;
import static com.eminus.client.settings.SettingsText.INGESTION_KEY;
import static com.eminus.client.settings.SettingsText.LOD_ANIMATIONS_KEY;
import static com.eminus.client.settings.SettingsText.LOD_QUALITY_KEY;
import static com.eminus.client.settings.SettingsText.SHADER_PACKS_CATEGORY_KEY;
import static com.eminus.client.settings.SettingsText.SHADER_PACK_LOD_KEY;
import static com.eminus.client.settings.SettingsText.TITLE_KEY;
import static com.eminus.client.settings.SettingsText.WORKER_THREADS_KEY;
import static com.eminus.client.settings.SettingsText.tooltip;

import com.eminus.compat.iris.IrisShaderPack;
import com.eminus.settings.Settings;
import com.eminus.settings.SettingsDraft;
import com.eminus.settings.SettingsService;
import com.eminus.settings.ShaderPackLod;
import com.mojang.serialization.Codec;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends DraftOptionsScreen {
    private static final boolean APPLY_ON_RELEASE = false;

    private final OptionInstance<Boolean> ingestion;
    private final OptionInstance<Integer> lodChoicePosition;
    private final OptionInstance<Integer> farRenderCells;
    private final OptionInstance<Integer> workerThreads;
    private final OptionInstance<Integer> detailDistancePosition;
    private final OptionInstance<Boolean> fog;
    private final OptionInstance<Boolean> fade;
    private final OptionInstance<ShaderPackLod> shaderPackLod;
    private final OptionInstance<Boolean> lodAnimations;

    public SettingsScreen(Screen lastScreen) {
        super(lastScreen, Component.translatable(TITLE_KEY), SettingsDraft.of(SettingsService.get()), HOLDS_DRAFT,
                DraftOptionsScreen::open);

        Settings settings = this.draft.settings();

        this.ingestion = OptionInstance.createBoolean(INGESTION_KEY, tooltip(SettingsText.toggleHint(INGESTION_KEY)),
                settings.ingestion(), value -> this.editFromOptions());
        this.lodChoicePosition = new OptionInstance<>(LOD_QUALITY_KEY,
                tooltip(SettingsText.withCost(SettingsText.lodChoiceHint(), OptionCost.LOD_QUALITY)),
                (caption, position) -> Options.genericValueLabel(caption,
                        SettingsText.lodChoice(SliderPositions.lodChoice(position))),
                coarseToFine(SliderPositions.LAST_LOD_CHOICE),
                SliderPositions.lodChoicePosition(settings.lodChoice()), value -> this.editFromOptions());
        this.farRenderCells = new OptionInstance<>(FAR_RENDER_CELLS_KEY,
                tooltip(SettingsText.withCost(SettingsText.hint(FAR_RENDER_CELLS_KEY), OptionCost.LOD_DISTANCE)),
                (caption, value) -> Options.genericValueLabel(caption, SettingsText.farRenderDistance(value)),
                new OptionInstance.IntRange(Settings.MIN_FAR_RENDER_CELLS, Settings.MAX_FAR_RENDER_CELLS,
                        APPLY_ON_RELEASE),
                settings.farRenderCells(), value -> this.editFromOptions());
        this.workerThreads = new OptionInstance<>(WORKER_THREADS_KEY, tooltip(WORKER_THREADS_KEY),
                (caption, value) -> Options.genericValueLabel(caption, value),
                new OptionInstance.IntRange(Settings.MIN_WORKER_THREADS, Settings.MAX_WORKER_THREADS,
                        APPLY_ON_RELEASE),
                settings.workerThreads(), value -> this.editFromOptions());
        this.detailDistancePosition = new OptionInstance<>(DETAIL_DISTANCE_KEY,
                position -> Tooltip.create(SettingsText.detailDistanceHint()),
                (caption, position) -> Options.genericValueLabel(caption,
                        SettingsText.detailDistance(SliderPositions.detailDistance(position))),
                coarseToFine(SliderPositions.LAST_DETAIL_DISTANCE),
                SliderPositions.detailDistancePosition(settings.detailDistance()), value -> this.editFromOptions());
        this.fog = OptionInstance.createBoolean(FOG_KEY, tooltip(SettingsText.toggleHint(FOG_KEY)), settings.fog(),
                value -> this.editFromOptions());
        this.fade = OptionInstance.createBoolean(FADE_KEY, tooltip(SettingsText.toggleHint(FADE_KEY)), settings.fade(),
                value -> this.editFromOptions());
        this.shaderPackLod = new OptionInstance<>(SHADER_PACK_LOD_KEY,
                tooltip(SettingsText.withCost(SettingsText.shaderPackLodHint(), OptionCost.SHADER_PACK_LOD)),
                (caption, value) -> SettingsText.shaderPackLod(value),
                new OptionInstance.Enum<>(List.of(ShaderPackLod.values()),
                        Codec.STRING.xmap(key -> ShaderPackLod.fromKey(key).orElse(Settings.DEFAULT_SHADER_PACK_LOD),
                                ShaderPackLod::key)),
                settings.shaderPackLod(), value -> this.editFromOptions());
        this.lodAnimations = OptionInstance.createBoolean(LOD_ANIMATIONS_KEY,
                tooltip(SettingsText.withCost(SettingsText.toggleHint(LOD_ANIMATIONS_KEY), OptionCost.LOD_ANIMATIONS)),
                settings.lodAnimations(),
                value -> this.editFromOptions());
    }

    @Override
    protected void addOptions() {
        Button customLodButton = Button.builder(Component.translatable(CUSTOM_LOD_KEY),
                        button -> this.minecraft.setScreen(CustomLodScreen.sharingDraft(this)))
                .tooltip(Tooltip.create(SettingsText.hint(CUSTOM_LOD_KEY)))
                .build();
        this.addHeader(GENERAL_CATEGORY_KEY);
        this.addSmallSlider(this.lodChoicePosition.createButton(this.options), this.lodChoicePosition,
                customLodButton);
        this.optionsList().addBig(this.farRenderCells);
        this.optionsList().addBig(this.detailDistancePosition);
        this.optionsList().addBig(this.ingestion);
        this.addHeader(APPEARANCE_CATEGORY_KEY);
        this.optionsList().addBig(this.fog);
        this.optionsList().addBig(this.fade);
        this.addHeader(BUILDING_CATEGORY_KEY);
        this.optionsList().addBig(this.workerThreads);
        if (IrisShaderPack.installed()) {
            this.addHeader(SHADER_PACKS_CATEGORY_KEY);
            this.optionsList().addBig(this.shaderPackLod);
            this.optionsList().addBig(this.lodAnimations);
        }
    }

    @Override
    protected void draftChanged() {
        super.draftChanged();
        int position = SliderPositions.lodChoicePosition(this.draft.settings().lodChoice());
        if (this.lodChoicePosition.get() != position) {
            this.lodChoicePosition.set(position);
            this.resetOption(this.lodChoicePosition);
        }
    }

    private void editFromOptions() {
        this.edit(settings -> new Settings(
                this.ingestion.get(),
                SliderPositions.lodChoice(this.lodChoicePosition.get()),
                settings.customLod(),
                this.farRenderCells.get(),
                this.workerThreads.get(),
                SliderPositions.detailDistance(this.detailDistancePosition.get()),
                this.fog.get(),
                this.fade.get(),
                this.shaderPackLod.get(),
                this.lodAnimations.get()));
    }

    // 1.21.1 keeps a slider's value set package-private, so the slider holds the position and the screen maps it.
    private static OptionInstance.IntRange coarseToFine(int lastPosition) {
        return new OptionInstance.IntRange(SliderPositions.FIRST, lastPosition, APPLY_ON_RELEASE);
    }
}
