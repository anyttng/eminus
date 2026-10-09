package com.eminus.compat.iris.dh;

import com.eminus.compat.iris.PackSources;

final class DhSources {
    static final String FRAGMENT_PROGRAM_MAIN =
            "void main() {\n    eminus_fragment();\n    " + PackSources.PACK_MAIN + "();\n}\n";
    static final String VERTEX_PROGRAM_MAIN =
            "void main() {\n    eminus_vertex();\n    " + PackSources.PACK_MAIN + "();\n    eminus_cull();\n}\n";

    private DhSources() {
    }

    static String spliceFragmentProgram(String packSource, String header) {
        return PackSources.insert(PackSources.renameMain(packSource), header, FRAGMENT_PROGRAM_MAIN);
    }

    static String spliceVertexProgram(String packSource, String header) {
        return PackSources.insert(PackSources.renameMain(packSource), header, VERTEX_PROGRAM_MAIN);
    }
}
