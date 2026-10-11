package com.eminus.compat.iris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PackSourcesTest {
    private static final String HEADER = "struct EminusFragment { vec4 color; };\n";
    private static final String NO_MAIN = "";
    private static final String PACK_PROGRAM = """
            #version 120

            varying vec4 color;

            void main() {
                gl_FragData[0] = color;
            }
            """;

    @Test
    void theHeaderDropsItsOwnVersionAndExtensions() {
        String header = PackSources.header("#version 330\n#extension GL_ARB_x : require\nuniform int A;\n");

        assertFalse(header.contains("#version"), header);
        assertFalse(header.contains("#extension"), header);
        assertTrue(header.contains("uniform int A;"), header);
    }

    @Test
    void aPackFileWithoutAVersionIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PackSources.insert("void main() {}\n", HEADER, NO_MAIN));
    }

    @Test
    void thePackMainIsRenamedWhateverItsSpacing() {
        assertEquals("#version 120\nvoid eminus_packMain() {}\n",
                PackSources.renameMain("#version 120\nvoid  main ( void ) {}\n"));
    }

    @Test
    void aPackProgramWithoutOneMainIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PackSources.renameMain("#version 120\nvoid mainly() {}\n"));
        assertThrows(IllegalArgumentException.class,
                () -> PackSources.renameMain("#version 120\n// void main()\nvoid main() {}\n"));
    }

    @Test
    void aPackFragmentProgramRunsOurCutoutBeforeThePackMain() {
        String spliced = PackSources.spliceFragmentProgram(PACK_PROGRAM, HEADER);
        String main = PackSources.FRAGMENT_PROGRAM_MAIN;

        assertTrue(spliced.startsWith("#version 120"), spliced);
        assertTrue(spliced.indexOf(HEADER) < spliced.indexOf("void eminus_packMain()"), spliced);
        assertTrue(spliced.endsWith(main), spliced);
        assertTrue(main.indexOf("eminus_fragment()") < main.indexOf(PackSources.PACK_MAIN), main);
    }

    @Test
    void aPackVertexProgramFillsTheInputsBeforeThePackMainAndCullsAfterIt() {
        String main = PackSources.VERTEX_PROGRAM_MAIN;
        String spliced = PackSources.spliceVertexProgram(PACK_PROGRAM, HEADER);

        assertTrue(spliced.endsWith(main), spliced);
        assertTrue(main.indexOf("eminus_vertex()") < main.indexOf(PackSources.PACK_MAIN), main);
        assertTrue(main.indexOf(PackSources.PACK_MAIN) < main.indexOf("eminus_cull()"), main);
    }
}
