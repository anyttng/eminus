package com.eminus.client.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    private final List<SmallSlider> sliders = new ArrayList<>();

    HeaderedOptionsList(Minecraft minecraft, int width, OptionsSubScreen screen) {
        super(minecraft, width, screen);
        this.screen = screen;
    }

    void addPlainHeader(Component text) {
        StringWidget widget = new StringWidget(this.getRowWidth(), Button.DEFAULT_HEIGHT, text, this.minecraft.font)
                .alignCenter();
        this.addEntry(OptionsList.Entry.big(List.of(widget), this.screen));
    }

    void addSmallSlider(AbstractWidget slider, OptionInstance<?> option, @Nullable AbstractWidget second) {
        this.sliders.add(new SmallSlider(slider, option, second));
        this.addSmall(slider, second);
    }

    void resetOption(OptionInstance<?> option) {
        for (int index = 0; index < this.sliders.size(); index++) {
            SmallSlider row = this.sliders.get(index);
            if (row.option() == option) {
                SmallSlider fresh = new SmallSlider(option.createButton(this.minecraft.options), option, row.second());
                this.sliders.set(index, fresh);
                this.replaceRow(row.slider(), OptionsList.Entry.small(fresh.slider(), fresh.second(), this.screen));
                return;
            }
        }
    }

    private void replaceRow(AbstractWidget slider, OptionsList.Entry replacement) {
        List<OptionsList.Entry> rows = this.children();
        for (int index = 0; index < rows.size(); index++) {
            OptionsList.Entry row = rows.get(index);
            if (row.children().contains(slider)) {
                rows.set(index, replacement);
                if (Objects.equals(this.getFocused(), row)) {
                    this.setFocused(replacement);
                }
                return;
            }
        }
    }

    // 1.21.1's addSmall for widgets keeps no option map, so super.applyUnsavedChanges never reaches these sliders.
    @Override
    public void applyUnsavedChanges() {
        super.applyUnsavedChanges();
        for (SmallSlider row : this.sliders) {
            if (row.slider() instanceof OptionInstance.OptionInstanceSliderButton<?> slider) {
                slider.applyUnsavedValue();
            }
        }
    }

    private record SmallSlider(AbstractWidget slider, OptionInstance<?> option, @Nullable AbstractWidget second) {
    }
}
