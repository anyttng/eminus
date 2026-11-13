package com.eminus.client.session;

import com.eminus.session.DimensionRuntime;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class StoreUpgradeNotice {
    private static final String MESSAGE_KEY = "chat.eminus.lod_version_changed";

    private static boolean pending;
    private static boolean noticed;

    private StoreUpgradeNotice() {
    }

    static void opened(DimensionRuntime runtime) {
        if (runtime.storeUpgraded() && !noticed) {
            noticed = true;
            pending = true;
        }
    }

    static void post(Minecraft minecraft) {
        if (!pending || minecraft.player == null || minecraft.screen != null) {
            return;
        }

        pending = false;
        minecraft.gui.getChat().addMessage(ChatPrefix.prefixed(Component.translatable(MESSAGE_KEY)), null,
                GuiMessageTag.systemSinglePlayer());
    }

    static void forget() {
        pending = false;
        noticed = false;
    }
}
