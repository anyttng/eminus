package com.eminus.client.settings;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

final class HeaderedOptionsList extends OptionsList {
    private static final int HEADER_LINES = 2;

    private final Screen screen;

    HeaderedOptionsList(Minecraft minecraft, int width, OptionsSubScreen screen) {
        super(minecraft, width, screen);
        this.screen = screen;
    }

    void addPlainHeader(Component text) {
        int lineHeight = this.screen.getFont().lineHeight;
        int paddingTop = this.children().isEmpty() ? 0 : lineHeight * HEADER_LINES;
        this.addEntry(new PlainHeader(this.screen, text, paddingTop), paddingTop + lineHeight * HEADER_LINES);
    }

    private static final class PlainHeader extends OptionsList.AbstractEntry {
        private final Screen screen;
        private final int paddingTop;
        private final StringWidget widget;

        private PlainHeader(Screen screen, Component text, int paddingTop) {
            this.screen = screen;
            this.paddingTop = paddingTop;
            this.widget = new StringWidget(text, screen.getFont());
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.widget);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            this.widget.setPosition(this.screen.width / 2 - this.widget.getWidth() / 2,
                    this.getContentY() + this.paddingTop);
            this.widget.extractRenderState(graphics, mouseX, mouseY, a);
        }
    }
}
