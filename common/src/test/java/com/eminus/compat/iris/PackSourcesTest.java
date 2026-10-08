package com.eminus.compat.iris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PackSourcesTest {
    private static final String NETHER = "world-1";
    private static final String HEADER = "struct EminusFragment { vec4 color; };\n";
    private static final String PACK = "#version 330 compatibility\n#extension GL_ARB_shader_texture_lod : enable\n\n"
            + "/* RENDERTARGETS: 0,1 */\nvoid eminus_emitFragment(EminusFragment fragment) {\n}\n";

    @Test
    void theDimensionFolderIsReadBeforeTheRoot() {
        assertEquals(List.of("/world-1/eminus_opaque.glsl", "/eminus_opaque.glsl"),
                PackSources.candidates(PackContract.OPAQUE_FILE, NETHER));
    }

    @Test
    void aDimensionWithoutAFolderReadsTheRootAlone() {
        assertEquals(List.of("/eminus_opaque.glsl"), PackSources.candidates(PackContract.OPAQUE_FILE, ""));
    }

    @Test
    void everyContractFileIsLookedForAtTheRootAndInEachDimensionFolder() {
        assertEquals(List.of("/eminus_opaque.glsl", "/world-1/eminus_opaque.glsl", "/eminus_translucent.glsl",
                "/world-1/eminus_translucent.glsl"), PackContract.paths(List.of(NETHER)));
    }

    @Test
    void theHeaderDropsItsOwnVersionAndExtensions() {
        String header = PackSources.header("#version 330\n#extension GL_ARB_x : require\nuniform int A;\n");

        assertFalse(header.contains("#version"), header);
        assertFalse(header.contains("#extension"), header);
        assertTrue(header.contains("uniform int A;"), header);
    }

    @Test
    void theHeaderLandsAfterThePackVersionAndExtensionsAndBeforeItsCode() {
        String spliced = PackSources.splice(PACK, HEADER);

        int extension = spliced.indexOf("#extension");
        int header = spliced.indexOf(HEADER);
        int function = spliced.indexOf("void eminus_emitFragment");
        assertTrue(spliced.startsWith("#version 330 compatibility"), spliced);
        assertTrue(extension < header && header < function, spliced);
    }

    @Test
    void theGeneratedMainCallsThePackFunctionAfterThePackCode() {
        String spliced = PackSources.splice(PACK, HEADER);

        assertTrue(spliced.endsWith(PackSources.MAIN), spliced);
        assertTrue(PackSources.MAIN.contains(PackContract.FUNCTION + "(eminus_fragment())"), PackSources.MAIN);
    }

    @Test
    void aPackFileWithoutAVersionIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PackSources.splice("void main() {}\n", HEADER));
    }
}
