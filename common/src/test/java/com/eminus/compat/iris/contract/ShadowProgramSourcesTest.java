package com.eminus.compat.iris.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShadowProgramSourcesTest {
    private static final String VERTEX = """
            #version 400 compatibility
            attribute vec3 at_midBlock;
            attribute vec4 at_tangent;
            attribute vec3 mc_Entity;
            attribute vec2 mc_midTexCoord;
            uniform sampler2D tex;
            out vec2 uv;
            void main() {
                uv = gl_MultiTexCoord0.xy;
                uint material = uint(mc_Entity.x - 10000.0);
                bool top = uv.y < mc_midTexCoord.y;
                gl_Position = ftransform();
            }
            """;
    private static final String FRAGMENT = """
            #version 400 compatibility
            uniform sampler2D noisetex, tex;
            in vec2 uv;
            void main() {
                vec4 base = textureLod(tex, uv, 0);
                vec4 noise = texture2D(noisetex, uv);
                // texture(tex, uv)
                gl_FragData[0] = texture(tex, uv) * base * noise;
            }
            """;

    @Test
    void theTexCoordIsTheFarVertexTileLocalCoordinate() {
        String source = ShadowProgramSources.vertex(VERTEX).source();

        assertFalse(source.contains("gl_MultiTexCoord0"), source);
        assertTrue(source.contains("uv = " + ShadowProgramSources.TEX_COORD_INPUT + ".xy;"), source);
    }

    @Test
    void theLightCoordinatesAndTheirMatrixKeepTheGameUnits() {
        String source = ShadowProgramSources.vertex(
                "#version 120\nvoid main() { vec2 light = (gl_TextureMatrix[1] * gl_MultiTexCoord2).xy; }\n").source();

        assertTrue(source.contains("(" + ShadowProgramSources.TEXTURE_MATRIX_INPUT + "[1] * "
                + ShadowProgramSources.LIGHT_COORD_INPUT + ").xy"), source);
    }

    @Test
    void theAttributesTheFarVertexFillsBecomeGlobalsOfTheirOwnType() {
        ShadowProgramSources.Vertex vertex = ShadowProgramSources.vertex(VERTEX);

        assertFalse(vertex.source().contains("attribute"), vertex.source());
        assertTrue(vertex.source().contains("uint(mc_Entity.x - 10000.0)"), vertex.source());
        assertTrue(vertex.inputs().contains("vec3 mc_Entity;"), vertex.inputs());
        assertTrue(vertex.inputs().contains("vec4 at_tangent;"), vertex.inputs());
        assertTrue(vertex.inputs().contains("mc_Entity.x = float(blockId);"), vertex.inputs());
        assertTrue(vertex.inputs().contains("mc_midTexCoord = vec2(0);"), vertex.inputs());
        assertFalse(vertex.inputs().contains("mc_midTexCoord.x"), vertex.inputs());
    }

    @Test
    void aScalarBlockIdTakesTheIdWhole() {
        String inputs = ShadowProgramSources.vertex("#version 120\nattribute float mc_Entity;\nvoid main() {}\n")
                .inputs();

        assertTrue(inputs.contains("mc_Entity = float(blockId);"), inputs);
    }

    @Test
    void anInputDeclaredBesideAnotherIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> ShadowProgramSources.vertex("#version 120\nattribute vec4 mc_Entity, at_tangent;\n"));
    }

    @Test
    void anAlbedoSampleInTheVertexStageIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ShadowProgramSources.vertex(
                "#version 120\nuniform sampler2D tex;\nvoid main() { gl_Position = texture2DLod(tex, vec2(0), 0); }\n"));
    }

    @Test
    void everyAlbedoLookupReadsTheFarAtlasAndOtherSamplersStayTheirOwn() {
        String source = ShadowProgramSources.fragment(FRAGMENT);

        assertTrue(source.contains("eminus_albedoLod(tex, uv, 0)"), source);
        assertTrue(source.contains("eminus_albedo(tex, uv) * base"), source);
        assertTrue(source.contains("texture2D(noisetex, uv)"), source);
        assertTrue(source.contains("// texture(tex, uv)"), source);
        assertTrue(source.contains("uniform sampler2D noisetex, tex;"), source);
    }

    @Test
    void aSamplerNamedTextureIsTheAlbedoAndTheFunctionOfTheSameNameIsNot() {
        assertEquals("uniform sampler2D texture;\nvec4 a = eminus_albedo(texture, uv);\n",
                ShadowProgramSources.fragment("uniform sampler2D texture;\nvec4 a = texture(texture, uv);\n"));
    }

    @Test
    void anAlbedoHandedToAFunctionOfThePacksOwnIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ShadowProgramSources.fragment(
                "uniform sampler2D tex;\nvec4 a = sampleAlbedo(tex, uv);\n"));
        assertThrows(IllegalArgumentException.class, () -> ShadowProgramSources.fragment(
                "uniform sampler2D tex;\nivec2 size = textureSize(tex, 0);\n"));
    }
}
