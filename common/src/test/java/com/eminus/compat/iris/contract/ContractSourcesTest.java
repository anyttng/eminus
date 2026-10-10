package com.eminus.compat.iris.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.eminus.client.render.far.FarDraw;
import com.eminus.compat.iris.PackContract;

import org.junit.jupiter.api.Test;

class ContractSourcesTest {
    private static final String NETHER = "world-1";
    private static final String HEADER = "struct EminusFragment { vec4 color; };\n";
    private static final String PACK = """
            #version 330 compatibility
            #extension GL_ARB_shader_texture_lod : enable

            /* RENDERTARGETS: 0,1 */
            void eminus_emitFragment(EminusFragment fragment) {
            }
            """;
    private static final String SHADOW_VERTEX = """
            #version 400 compatibility

            vec4 eminus_shadowPosition(vec4 shadowClipPosition) {
                return shadowClipPosition;
            }
            """;

    @Test
    void theDimensionFolderIsReadBeforeTheRoot() {
        assertEquals(List.of("/world-1/eminus_opaque.glsl", "/eminus_opaque.glsl"),
                ContractSources.candidates(PackContract.OPAQUE_FILE, NETHER));
    }

    @Test
    void aDimensionWithoutAFolderReadsTheRootAlone() {
        assertEquals(List.of("/eminus_opaque.glsl"), ContractSources.candidates(PackContract.OPAQUE_FILE, ""));
    }

    @Test
    void everyContractFileIsLookedForAtTheRootAndInEachDimensionFolder() {
        assertEquals(List.of("/eminus_opaque.glsl", "/world-1/eminus_opaque.glsl", "/eminus_translucent.glsl",
                "/world-1/eminus_translucent.glsl", "/eminus_shadow.glsl", "/world-1/eminus_shadow.glsl",
                "/eminus_shadow_translucent.glsl", "/world-1/eminus_shadow_translucent.glsl",
                "/eminus_shadow_vertex.glsl", "/world-1/eminus_shadow_vertex.glsl", "/eminus_vertex.glsl",
                "/world-1/eminus_vertex.glsl"),
                PackContract.paths(List.of(NETHER)));
    }

    @Test
    void theVertexHookDrivesTheShadowProgramOverTheVersionThreeShadowHook() {
        assertEquals(FarDraw.PackVertex.HOOK, ContractSources.shadowStage(true, true));
        assertEquals(FarDraw.PackVertex.HOOK, ContractSources.shadowStage(true, false));
    }

    @Test
    void theVersionThreeShadowHookDrivesTheShadowProgramWithoutTheVertexHook() {
        assertEquals(FarDraw.PackVertex.SHADOW_HOOK, ContractSources.shadowStage(false, true));
        assertEquals(FarDraw.PackVertex.OURS, ContractSources.shadowStage(false, false));
    }

    @Test
    void theHeaderLandsAfterThePackVersionAndExtensionsAndBeforeItsCode() {
        String spliced = ContractSources.splice(PACK, HEADER);

        int extension = spliced.indexOf("#extension");
        int header = spliced.indexOf(HEADER);
        int function = spliced.indexOf("void eminus_emitFragment");
        assertTrue(spliced.startsWith("#version 330 compatibility"), spliced);
        assertTrue(extension < header && header < function, spliced);
    }

    @Test
    void theGeneratedMainCallsThePackFunctionAfterThePackCode() {
        String spliced = ContractSources.splice(PACK, HEADER);

        assertTrue(spliced.endsWith(ContractSources.MAIN), spliced);
        assertTrue(ContractSources.MAIN.contains(PackContract.FUNCTION + "(eminus_fragment())"),
                ContractSources.MAIN);
    }

    @Test
    void theVertexSpliceKeepsOurOwnMainAndAddsNone() {
        String ours = "void main() {\n    gl_Position = eminus_shadowPosition(vec4(0.0));\n}\n";
        String spliced = ContractSources.spliceVertex(SHADOW_VERTEX, ours);

        assertTrue(spliced.startsWith("#version 400 compatibility"), spliced);
        assertTrue(spliced.indexOf(ours) < spliced.indexOf("vec4 eminus_shadowPosition"), spliced);
        assertFalse(spliced.contains(PackContract.FUNCTION), spliced);
    }
}
