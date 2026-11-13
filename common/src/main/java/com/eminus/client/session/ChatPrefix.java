package com.eminus.client.session;

import com.eminus.Eminus;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;

final class ChatPrefix {
    private static final String SPACE = " ";

    static MutableComponent prefixed(Component message) {
        return Component.empty()
                .append(ComponentUtils.wrapInSquareBrackets(Component.literal(Eminus.NAME))
                        .withStyle(ChatFormatting.GOLD))
                .append(SPACE)
                .append(message);
    }

    private ChatPrefix() {
    }
}
