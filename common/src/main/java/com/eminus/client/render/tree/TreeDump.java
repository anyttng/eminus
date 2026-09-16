package com.eminus.client.render.tree;

import com.eminus.client.render.far.FarRenderer;
import com.eminus.client.session.ClientSession;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class TreeDump {
    private static final String NO_RENDERER = "No far renderer is running.";
    private static final String LOGGED = "The node chain of the cell under the player is in the log.";

    public static Component at() {
        Minecraft client = Minecraft.getInstance();
        FarRenderer renderer = ClientSession.renderer();

        if (renderer == null || client.player == null) {
            return Component.literal(NO_RENDERER);
        }

        renderer.describe(client.player.blockPosition());
        return Component.literal(LOGGED);
    }

    private TreeDump() {
    }
}
