package com.eminus.client.model.game;

import net.minecraft.client.renderer.block.model.BakedQuad;

public record LayeredQuad(BakedQuad quad, boolean translucent) {
}
