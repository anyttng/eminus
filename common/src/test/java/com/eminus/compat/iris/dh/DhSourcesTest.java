package com.eminus.compat.iris.dh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.compat.iris.PackSources;

import org.junit.jupiter.api.Test;

class DhSourcesTest {
    private static final String HEADER = "struct EminusFragment { vec4 color; };\n";
    private static final String DH_PROGRAM = "#version 120\n\nvarying vec4 color;\n\nvoid main() {\n"
            + "    gl_FragData[0] = color;\n}\n";

    @Test
    void theDhFragmentRunsOurCutoutBeforeThePackMain() {
        String spliced = DhSources.spliceFragmentProgram(DH_PROGRAM, HEADER);
        String main = DhSources.FRAGMENT_PROGRAM_MAIN;

        assertTrue(spliced.startsWith("#version 120"), spliced);
        assertTrue(spliced.indexOf(HEADER) < spliced.indexOf("void eminus_packMain()"), spliced);
        assertTrue(spliced.endsWith(main), spliced);
        assertTrue(main.indexOf("eminus_fragment()") < main.indexOf(PackSources.PACK_MAIN), main);
    }

    @Test
    void theDhVertexFillsTheInputsBeforeThePackMainAndCullsAfterIt() {
        String main = DhSources.VERTEX_PROGRAM_MAIN;
        String spliced = DhSources.spliceVertexProgram(DH_PROGRAM, HEADER);

        assertTrue(spliced.endsWith(main), spliced);
        assertTrue(main.indexOf("eminus_vertex()") < main.indexOf(PackSources.PACK_MAIN), main);
        assertTrue(main.indexOf(PackSources.PACK_MAIN) < main.indexOf("eminus_cull()"), main);
    }
}
