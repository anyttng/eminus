package com.eminus.client.gpu.opengl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eminus.gpu.pipeline.PipelineSpec;

import net.minecraft.resources.Identifier;

public final class GlslSource {
    public static final String VERTEX_INDEX_ALIAS = "#define gl_VertexIndex gl_VertexID";

    private static final Pattern INCLUDE = Pattern.compile("^\\s*#moj_import\\s*<([^>]+)>\\s*$");
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("^(\\s*)(#version\\s+(\\d+))(.*)$");
    private static final String VERSION = "#version";
    private static final int NO_VERSION = 0;
    private static final String DEFINE = "#define ";
    private static final String LINE = "\n";
    private static final int MAX_INCLUDE_DEPTH = 8;

    private GlslSource() {
    }

    public static String compose(String source, List<PipelineSpec.Define> defines,
            Function<Identifier, Optional<String>> includes) {
        ImportedVersion imported = new ImportedVersion();
        List<String> lines = new ArrayList<>();
        for (String line : source.split(LINE, -1)) {
            lines.add(expand(line, includes, 0, imported));
        }

        StringBuilder composed = new StringBuilder();
        boolean versionSeen = false;
        for (String line : lines) {
            if (versionSeen || !line.stripLeading().startsWith(VERSION)) {
                composed.append(line).append(LINE);
            } else {
                versionSeen = true;
                composed.append(raised(line, imported.highest)).append(LINE);
                composed.append(VERTEX_INDEX_ALIAS).append(LINE);
                for (PipelineSpec.Define define : defines) {
                    composed.append(DEFINE).append(define.name());
                    if (define.value() != null) {
                        composed.append(' ').append(literal(define.value()));
                    }
                    composed.append(LINE);
                }
            }
        }

        composed.setLength(composed.length() - LINE.length());
        return composed.toString();
    }

    private static String expand(String line, Function<Identifier, Optional<String>> includes, int depth,
            ImportedVersion imported) {
        Matcher version = VERSION_DIRECTIVE.matcher(line);
        if (depth > 0 && version.matches()) {
            imported.raise(Integer.parseInt(version.group(3)));
            return version.group(1) + "/*" + version.group(2) + "*/" + version.group(4);
        }

        Matcher include = INCLUDE.matcher(line);
        if (!include.matches()) {
            return line;
        }

        if (depth >= MAX_INCLUDE_DEPTH) {
            throw new IllegalStateException("Include <" + include.group(1) + "> nests deeper than " + MAX_INCLUDE_DEPTH);
        }

        Identifier id = Identifier.tryParse(include.group(1));
        if (id == null) {
            throw new IllegalStateException("Include <" + include.group(1) + "> is not an identifier");
        }

        String body = includes.apply(id)
                .orElseThrow(() -> new IllegalStateException("Include <" + id + "> was not found"));
        StringBuilder expanded = new StringBuilder();
        for (String included : body.split(LINE, -1)) {
            expanded.append(expand(included, includes, depth + 1, imported)).append(LINE);
        }
        expanded.setLength(expanded.length() - LINE.length());
        return expanded.toString();
    }

    private static String raised(String line, int importedVersion) {
        Matcher version = VERSION_DIRECTIVE.matcher(line);
        if (!version.matches() || Integer.parseInt(version.group(3)) >= importedVersion) {
            return line;
        }

        return line.substring(0, version.start(3)) + importedVersion + line.substring(version.end(3));
    }

    private static String literal(Number value) {
        return switch (value) {
            case Integer integer -> Integer.toString(integer);
            case Float real -> String.format(Locale.ROOT, "%s", real);
            default -> throw new IllegalArgumentException("Define value " + value + " is neither int nor float");
        };
    }

    private static final class ImportedVersion {
        private int highest = NO_VERSION;

        private void raise(int version) {
            highest = Math.max(highest, version);
        }
    }
}
