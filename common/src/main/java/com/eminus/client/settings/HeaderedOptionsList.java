package com.eminus.client.settings;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

final class HeaderedOptionsList extends OptionsList {
    private final Screen screen;
    private final List<AbstractWidget> sliders = new ArrayList<>();

    HeaderedOptionsList(Minecraft minecraft, int width, OptionsSubScreen screen) {
        super(minecraft, width, screen);
        this.screen = screen;
    }

    void addPlainHeader(Component text) {
        StringWidget widget = new StringWidget(this.getRowWidth(), Button.DEFAULT_HEIGHT, text, this.minecraft.font)
                .alignCenter();
        this.addEntry(OptionsList.Entry.big(List.of(widget), this.screen));
    }

    void addSmallSlider(AbstractWidget slider, @Nullable AbstractWidget second) {
        this.sliders.add(slider);
        this.addSmall(slider, second);
    }

    // 1.21.1's addSmall for widgets keeps no option map, so super.applyUnsavedChanges never reaches these sliders.
    @Override
    public void applyUnsavedChanges() {
        super.applyUnsavedChanges();
        for (AbstractWidget widget : this.sliders) {
            if (widget instanceof OptionInstance.OptionInstanceSliderButton<?> slider) {
                slider.applyUnsavedValue();
            }
        }
    }
}
