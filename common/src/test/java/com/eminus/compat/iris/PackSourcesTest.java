package com.eminus.compat.iris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PackSourcesTest {
    private static final String HEADER = "struct EminusFragment { vec4 color; };\n";
    private static final String NO_MAIN = "";

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
}
