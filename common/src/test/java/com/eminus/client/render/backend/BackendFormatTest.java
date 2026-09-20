package com.eminus.client.render.backend;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class BackendFormatTest {
    @Test
    void theProbedDepthFormatHasNoStencilAspect() {
        assertFalse(BackendCheck.DEPTH_FORMAT.hasStencilAspect(), BackendCheck.DEPTH_FORMAT.name());
    }
}
