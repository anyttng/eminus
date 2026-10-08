package com.eminus.compat.iris;

import java.util.List;

import org.jspecify.annotations.Nullable;

final class PackSources {
    static final String MAIN = "void main() {\n    " + PackContract.FUNCTION + "(eminus_fragment());\n}\n";

    private static final String VERSION = "#version";
    private static final String EXTENSION = "#extension";
    private static final String LINE = "\n";
    private static final String NO_MAIN = "";

    private PackSources() {
    }

    static List<String> candidates(String file, @Nullable String folder) {
        return folder == null || folder.isEmpty() ? List.of(PackContract.ROOT + file)
                : List.of(PackContract.ROOT + folder + PackContract.ROOT + file, PackContract.ROOT + file);
    }

    static String header(String preprocessed) {
        StringBuilder header = new StringBuilder();
        for (String line : preprocessed.split(LINE, -1)) {
            if (!directive(line)) {
                header.append(line).append(LINE);
            }
        }
        return header.toString();
    }

    static String splice(String packSource, String header) {
        return splice(packSource, header, MAIN);
    }

    static String spliceVertex(String packSource, String header) {
        return splice(packSource, header, NO_MAIN);
    }

    private static String splice(String packSource, String header, String main) {
        String[] lines = packSource.split(LINE, -1);
        int insertAt = -1;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].strip();
            if (directive(line)) {
                insertAt = index + 1;
            } else if (!line.isEmpty()) {
                break;
            }
        }
        if (insertAt < 0) {
            throw new IllegalArgumentException("No " + VERSION + " directive opens the pack file");
        }

        StringBuilder spliced = new StringBuilder();
        for (int index = 0; index < lines.length; index++) {
            if (index == insertAt) {
                spliced.append(header);
            }
            spliced.append(lines[index]).append(LINE);
        }
        if (insertAt == lines.length) {
            spliced.append(header);
        }
        return spliced.append(main).toString();
    }

    private static boolean directive(String line) {
        String stripped = line.strip();
        return stripped.startsWith(VERSION) || stripped.startsWith(EXTENSION);
    }
}
