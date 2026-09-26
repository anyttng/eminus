package com.eminus.client.gpu.opengl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eminus.gpu.Format;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL14C;

class OpenGlTypesTest {
    @Test
    void everyPortFormatSurvivesTheTripThroughItsInternalFormat() {
        for (Format format : Format.values()) {
            assertEquals(format, OpenGlTypes.format(OpenGlTypes.internalFormat(format)));
        }
    }

    @Test
    void theGamesNormalizedDepthTextureHasAPortFormat() {
        int gameDepth = GL14C.GL_DEPTH_COMPONENT32;

        assertEquals(gameDepth, OpenGlTypes.internalFormat(OpenGlTypes.format(gameDepth)));
    }
}
