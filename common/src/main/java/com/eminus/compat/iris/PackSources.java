package com.eminus.compat.iris;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PackSources {
    public static final String PACK_MAIN = "eminus_packMain";

    private static final String VERSION = "#version";
    private static final String EXTENSION = "#extension";
    private static final String LINE = "\n";
    private static final Pattern MAIN_SIGNATURE = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final String RENAMED_SIGNATURE = "void " + PACK_MAIN + "()";

    private PackSources() {
    }

    public static String header(String preprocessed) {
        StringBuilder header = new StringBuilder();
        for (String line : preprocessed.split(LINE, -1)) {
            if (!directive(line)) {
                header.append(line).append(LINE);
            }
        }
        return header.toString();
    }

    public static String insert(String packSource, String header, String main) {
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

    public static String renameMain(String packSource) {
        Matcher main = MAIN_SIGNATURE.matcher(packSource);
        if (!main.find()) {
            throw new IllegalArgumentException("No main function in the pack program");
        }

        int start = main.start();
        int end = main.end();
        if (main.find()) {
            throw new IllegalArgumentException("More than one main function in the pack program");
        }
        return packSource.substring(0, start) + RENAMED_SIGNATURE + packSource.substring(end);
    }

    private static boolean directive(String line) {
        String stripped = line.strip();
        return stripped.startsWith(VERSION) || stripped.startsWith(EXTENSION);
    }
}
