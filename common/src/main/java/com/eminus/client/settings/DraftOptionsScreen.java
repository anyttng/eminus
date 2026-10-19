package com.eminus.client.settings;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.eminus.settings.Settings;
import com.eminus.settings.SettingsDraft;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

abstract class DraftOptionsScreen extends OptionsSubScreen {
    private static final Component APPLY_CHANGES = Component.translatable(SettingsText.APPLY_CHANGES_KEY);
    private static final int FOOTER_SPACING = 8;
    static final boolean HOLDS_DRAFT = true;
    static final boolean SHARES_DRAFT = false;

    protected final SettingsDraft draft;
    private final boolean holdsDraft;
    private final Consumer<Screen> back;
    private @Nullable Button applyChanges;
    private @Nullable HeaderedOptionsList headeredList;

    DraftOptionsScreen(Screen lastScreen, Component title, SettingsDraft draft, boolean holdsDraft,
            Consumer<Screen> back) {
        super(lastScreen, Minecraft.getInstance().options, title);
        this.draft = draft;
        this.holdsDraft = holdsDraft;
        this.back = back;
    }

    static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    protected void addContents() {
        this.headeredList = this.layout.addToContents(new HeaderedOptionsList(this.minecraft, this.width, this));
        this.list = this.headeredList;
        this.addOptions();
    }

    protected HeaderedOptionsList optionsList() {
        return Objects.requireNonNull(this.headeredList, "addContents builds it before addOptions");
    }

    protected void addHeader(String key) {
        if (this.headeredList != null) {
            this.headeredList.addPlainHeader(Component.translatable(key).withStyle(ChatFormatting.UNDERLINE,
                    ChatFormatting.BOLD));
        }
    }

    protected void addSmallSlider(AbstractWidget slider, @Nullable AbstractWidget second) {
        if (this.headeredList != null) {
            this.headeredList.addSmallSlider(slider, second);
        }
    }

    @Override
    protected void addFooter() {
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(FOOTER_SPACING));
        if (this.holdsDraft) {
            this.applyChanges = footer.addChild(Button.builder(APPLY_CHANGES, button -> this.write()).build());
            footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
        } else {
            footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
        }
        this.draftChanged();
    }

    protected void edit(UnaryOperator<Settings> edit) {
        this.draft.edit(edit);
        this.draftChanged();
    }

    protected void draftChanged() {
        if (this.applyChanges != null) {
            this.applyChanges.active = this.draft.changed();
        }
    }

    // A screen returned to is not built again, so its buttons read the draft here.
    @Override
    public void added() {
        this.draftChanged();
    }

    // Vanilla's OptionsSubScreen rewrites options.txt here, and this screen owns no vanilla option.
    @Override
    public void removed() {
    }

    @Override
    public void onClose() {
        if (!this.holdsDraft) {
            this.applyUnsavedChanges();
        }

        this.back.accept(this.lastScreen);
    }

    private void write() {
        this.applyUnsavedChanges();
        this.draft.write();
        this.draftChanged();
    }

    private void applyUnsavedChanges() {
        if (this.list != null) {
            this.list.applyUnsavedChanges();
        }
    }
}
