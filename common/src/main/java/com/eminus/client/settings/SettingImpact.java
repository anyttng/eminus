package com.eminus.client.settings;

import net.minecraft.ChatFormatting;

public enum SettingImpact {
    LOW("low", ChatFormatting.GREEN),
    MEDIUM("medium", ChatFormatting.YELLOW),
    HIGH("high", ChatFormatting.RED);

    private final String key;
    private final ChatFormatting style;

    SettingImpact(String key, ChatFormatting style) {
        this.key = key;
        this.style = style;
    }

    public String key() {
        return key;
    }

    public ChatFormatting style() {
        return style;
    }
}
