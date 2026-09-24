package com.eminus.client.gpu.game;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.eminus.client.render.backend.BackendCheck;

import com.mojang.renderpearl.api.GpuFormat;

import org.junit.jupiter.api.Test;

class BackendFormatTest {
    @Test
    void theProbedDepthFormatHasNoStencilAspect() {
        GpuFormat depth = GameTypes.format(BackendCheck.DEPTH_FORMAT);
        assertFalse(depth.hasStencilAspect(), depth.name());
    }
}
