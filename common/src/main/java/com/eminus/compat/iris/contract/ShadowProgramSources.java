package com.eminus.compat.iris.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ShadowProgramSources {
    static final String INPUTS_FUNCTION = "eminus_packInputs";
    static final String TEX_COORD_INPUT = "eminus_multiTexCoord0";
    static final String LIGHT_COORD_INPUT = "eminus_multiTexCoord1";
    static final String TEXTURE_MATRIX_INPUT = "eminus_textureMatrix";

    private static final String TEXTURE_MATRIX = "gl_TextureMatrix";
    private static final Map<String, String> VERTEX_RENAMES = Map.of(
            "gl_MultiTexCoord0", TEX_COORD_INPUT,
            "gl_MultiTexCoord1", LIGHT_COORD_INPUT,
            "gl_MultiTexCoord2", LIGHT_COORD_INPUT,
            TEXTURE_MATRIX, TEXTURE_MATRIX_INPUT);
    private static final String BLOCK_ID_INPUT = "mc_Entity";
    private static final Set<String> ZERO_INPUTS = Set.of("mc_midTexCoord", "at_midBlock", "at_tangent");
    private static final Set<String> INPUT_QUALIFIERS = Set.of("in", "attribute");
    private static final Set<String> ALBEDO = Set.of("gtexture", "tex", "texture", "gcolor");
    private static final Map<String, String> ALBEDO_SAMPLES = Map.of(
            "texture", "eminus_albedo",
            "texture2D", "eminus_albedo",
            "textureLod", "eminus_albedoLod",
            "texture2DLod", "eminus_albedoLod",
            "textureGrad", "eminus_albedoGrad",
            "texture2DGrad", "eminus_albedoGrad",
            "texture2DGradARB", "eminus_albedoGrad");
    private static final Map<String, String> VECTOR_COMPONENTS = Map.of("vec", "float", "ivec", "int", "uvec", "uint");
    private static final Set<String> SCALARS = Set.of("float", "int", "uint");
    private static final String UNIFORM = "uniform";
    private static final String LAYOUT = "layout";
    private static final String OPEN = "(";
    private static final String CLOSE = ")";
    private static final String COMMA = ",";
    private static final String END = ";";
    private static final Set<String> STATEMENT_BREAKS = Set.of(END, "{", "}");
    private static final String BLOCK_ID = "blockId";
    private static final String FIRST_COMPONENT = ".x";
    private static final String REMOVED = "";

    private ShadowProgramSources() {
    }

    record Vertex(String source, String inputs) {
    }

    private record Token(int start, int end, String text) {
        boolean is(String other) {
            return text.equals(other);
        }
    }

    private record Edit(int start, int end, String text) {
    }

    static Vertex vertex(String packSource) {
        List<Token> tokens = tokens(packSource);
        List<Edit> edits = new ArrayList<>();
        StringBuilder declarations = new StringBuilder();
        StringBuilder assignments = new StringBuilder();
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            String renamed = VERTEX_RENAMES.get(token.text());
            if (renamed != null) {
                edits.add(new Edit(token.start(), token.end(), renamed));
            } else if (token.is(BLOCK_ID_INPUT) || ZERO_INPUTS.contains(token.text())) {
                input(tokens, index, edits, declarations, assignments);
            } else if (albedo(tokens, index) && !declaration(tokens, index)) {
                throw new IllegalArgumentException("The shadow program samples " + token.text()
                        + " in its vertex stage");
            }
        }
        String inputs = declarations + "void " + INPUTS_FUNCTION + "(int " + BLOCK_ID + ") {\n" + assignments + "}\n";
        return new Vertex(apply(packSource, edits), inputs);
    }

    static String fragment(String packSource) {
        List<Token> tokens = tokens(packSource);
        List<Edit> edits = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (token.is(TEXTURE_MATRIX)) {
                edits.add(new Edit(token.start(), token.end(), TEXTURE_MATRIX_INPUT));
            }
            if (!albedo(tokens, index) || declaration(tokens, index)) {
                continue;
            }

            Token sample = index >= 2 && tokens.get(index - 1).is(OPEN) ? tokens.get(index - 2) : null;
            String lookup = sample == null ? null : ALBEDO_SAMPLES.get(sample.text());
            if (sample == null || lookup == null || index + 1 >= tokens.size() || !tokens.get(index + 1).is(COMMA)) {
                throw new IllegalArgumentException("The shadow program uses " + tokens.get(index).text()
                        + " other than as the first argument of a texture lookup");
            }
            edits.add(new Edit(sample.start(), sample.end(), lookup));
        }
        return apply(packSource, edits);
    }

    private static void input(List<Token> tokens, int index, List<Edit> edits, StringBuilder declarations,
            StringBuilder assignments) {
        int start = statementStart(tokens, index);
        int first = afterLayout(tokens, start);
        if (!INPUT_QUALIFIERS.contains(tokens.get(first).text())) {
            return;
        }

        String name = tokens.get(index).text();
        if (index != first + 2 || index + 1 >= tokens.size() || !tokens.get(index + 1).is(END)) {
            throw new IllegalArgumentException("The shadow program declares " + name + " other than alone");
        }

        String type = tokens.get(first + 1).text();
        declarations.append(type).append(' ').append(name).append(END).append('\n');
        assignments.append("    ").append(name).append(" = ").append(type).append("(0)").append(END).append('\n');
        if (name.equals(BLOCK_ID_INPUT)) {
            assignments.append("    ").append(name).append(SCALARS.contains(type) ? REMOVED : FIRST_COMPONENT)
                    .append(" = ").append(component(type, name)).append(OPEN).append(BLOCK_ID).append(CLOSE)
                    .append(END).append('\n');
        }
        edits.add(new Edit(tokens.get(start).start(), tokens.get(index + 1).end(), REMOVED));
    }

    private static String component(String type, String name) {
        if (SCALARS.contains(type)) {
            return type;
        }
        for (Map.Entry<String, String> vector : VECTOR_COMPONENTS.entrySet()) {
            if (type.length() == vector.getKey().length() + 1 && type.startsWith(vector.getKey())) {
                return vector.getValue();
            }
        }
        throw new IllegalArgumentException("The shadow program declares " + name + " as " + type);
    }

    private static boolean albedo(List<Token> tokens, int index) {
        return ALBEDO.contains(tokens.get(index).text())
                && (index + 1 >= tokens.size() || !tokens.get(index + 1).is(OPEN));
    }

    private static boolean declaration(List<Token> tokens, int index) {
        return tokens.get(afterLayout(tokens, statementStart(tokens, index))).is(UNIFORM);
    }

    private static int statementStart(List<Token> tokens, int index) {
        int start = index;
        while (start > 0 && !STATEMENT_BREAKS.contains(tokens.get(start - 1).text())) {
            start--;
        }
        return start;
    }

    private static int afterLayout(List<Token> tokens, int start) {
        if (!tokens.get(start).is(LAYOUT)) {
            return start;
        }

        int index = start + 1;
        while (index < tokens.size() && !tokens.get(index).is(CLOSE)) {
            index++;
        }
        return Math.min(index + 1, tokens.size() - 1);
    }

    private static String apply(String source, List<Edit> edits) {
        StringBuilder applied = new StringBuilder(source);
        for (int index = edits.size() - 1; index >= 0; index--) {
            Edit edit = edits.get(index);
            applied.replace(edit.start(), edit.end(), edit.text());
        }
        return applied.toString();
    }

    private static List<Token> tokens(String source) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        boolean lineStart = true;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '\n') {
                lineStart = true;
                index++;
            } else if (Character.isWhitespace(current)) {
                index++;
            } else if (lineStart && current == '#') {
                index = lineEnd(source, index);
            } else if (source.startsWith("//", index)) {
                index = lineEnd(source, index);
            } else if (source.startsWith("/*", index)) {
                int close = source.indexOf("*/", index + 2);
                index = close < 0 ? source.length() : close + 2;
            } else {
                lineStart = false;
                int end = index + 1;
                if (word(current)) {
                    while (end < source.length() && word(source.charAt(end))) {
                        end++;
                    }
                }
                tokens.add(new Token(index, end, source.substring(index, end)));
                index = end;
            }
        }
        return tokens;
    }

    private static boolean word(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private static int lineEnd(String source, int index) {
        int end = source.indexOf('\n', index);
        return end < 0 ? source.length() : end;
    }
}
