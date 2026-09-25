package com.eminus.client.gpu.opengl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.eminus.gpu.pipeline.PipelineSpec;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

class GlslSourceTest {
    private static final Map<String, String> INCLUDES = Map.of(
            "minecraft:globals.glsl", "layout(std140) uniform Globals { float GameTime; };",
            "eminus:outer.glsl", "#moj_import <eminus:inner.glsl>\nfloat outer() { return inner(); }",
            "eminus:inner.glsl", "float inner() { return 1.0; }",
            "eminus:loop.glsl", "#moj_import <eminus:loop.glsl>",
            "minecraft:versioned.glsl", "#version 330\n\nvec4 sample_lightmap() { return vec4(1.0); }",
            "eminus:newer.glsl", "#version 400\nfloat newer() { return 2.0; }");

    private static Optional<String> include(Identifier id) {
        return Optional.ofNullable(INCLUDES.get(id.toString()));
    }

    @Test
    void definesAndTheVertexIndexAliasFollowTheVersionLine() {
        String composed = GlslSource.compose("#version 330\n#extension GL_ARB_separate_shader_objects : require\nvoid main() {}",
                List.of(new PipelineSpec.Define("FULL_COVERAGE", null), new PipelineSpec.Define("MAX_SAMPLES", 8),
                        new PipelineSpec.Define("ALPHA_CUTOUT", 0.5F)),
                GlslSourceTest::include);

        assertEquals("#version 330\n" + GlslSource.VERTEX_INDEX_ALIAS + "\n#define FULL_COVERAGE\n#define MAX_SAMPLES 8\n"
                + "#define ALPHA_CUTOUT 0.5\n#extension GL_ARB_separate_shader_objects : require\nvoid main() {}",
                composed);
    }

    @Test
    void includesExpandInPlaceAndNest() {
        String composed = GlslSource.compose("#version 330\n#moj_import <minecraft:globals.glsl>\n#moj_import <eminus:outer.glsl>\nvoid main() {}",
                List.of(), GlslSourceTest::include);

        assertEquals("#version 330\n" + GlslSource.VERTEX_INDEX_ALIAS + "\n"
                + "layout(std140) uniform Globals { float GameTime; };\n"
                + "float inner() { return 1.0; }\nfloat outer() { return inner(); }\nvoid main() {}", composed);
    }

    @Test
    void anIncludesOwnVersionLineIsCommentedOut() {
        String composed = GlslSource.compose("#version 330\n#moj_import <minecraft:versioned.glsl>\nvoid main() {}",
                List.of(), GlslSourceTest::include);

        assertEquals("#version 330\n" + GlslSource.VERTEX_INDEX_ALIAS + "\n/*#version 330*/\n\n"
                + "vec4 sample_lightmap() { return vec4(1.0); }\nvoid main() {}", composed);
    }

    @Test
    void anIncludesHigherVersionRaisesTheShaders() {
        String composed = GlslSource.compose("#version 330\n#moj_import <eminus:newer.glsl>\nvoid main() {}",
                List.of(), GlslSourceTest::include);

        assertEquals("#version 400\n" + GlslSource.VERTEX_INDEX_ALIAS + "\n/*#version 400*/\n"
                + "float newer() { return 2.0; }\nvoid main() {}", composed);
    }

    @Test
    void aMissingIncludeIsNamed() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> GlslSource.compose("#version 330\n#moj_import <eminus:absent.glsl>", List.of(), GlslSourceTest::include));

        assertTrue(refused.getMessage().contains("eminus:absent.glsl"), refused.getMessage());
    }

    @Test
    void anIncludeCycleStopsAtTheDepthLimit() {
        assertThrows(IllegalStateException.class,
                () -> GlslSource.compose("#version 330\n#moj_import <eminus:loop.glsl>", List.of(), GlslSourceTest::include));
    }
}
