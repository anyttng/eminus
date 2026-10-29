package com.eminus.compat.iris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eminus.client.gpu.opengl.GlslSource;
import com.eminus.gpu.Location;

import org.junit.jupiter.api.Test;

class PackHeaderNamesTest {
    private static final String SHADERS = "/assets/eminus/shaders/";
    private static final String ASSETS = "/assets/";
    private static final String INCLUDES = "/shaders/include/";
    private static final String FRAGMENT = "core/far_pack.fsh";
    private static final String VERTEX = "core/far_pack.vsh";
    private static final String BEAM_FRAGMENT = "core/far_pack_beams.fsh";
    private static final String BEAM_VERTEX = "core/far_pack_beams.vsh";
    private static final List<String> PREFIXES = List.of("eminus", "Eminus", "EMINUS", "iris_");
    private static final Set<String> ENTRY_POINTS = Set.of("main");
    private static final Pattern COMMENT = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_]\\w*|\\d[\\w.]*|\\S");
    private static final String LINE = "\n";
    private static final String DIRECTIVE = "#";
    private static final String STRUCT = "struct";
    private static final String LAYOUT = "layout";
    private static final String OPEN_PAREN = "(";
    private static final String CLOSE_PAREN = ")";
    private static final String OPEN_BRACKET = "[";
    private static final String CLOSE_BRACKET = "]";
    private static final String OPEN_BRACE = "{";
    private static final String CLOSE_BRACE = "}";
    private static final String END = ";";
    private static final String SEPARATOR = ",";
    private static final String ASSIGN = "=";

    @Test
    void everyFragmentHeaderNameCarriesAReservedPrefix() {
        assertEquals(List.of(), unprefixed(declared(header(FRAGMENT))), FRAGMENT);
    }

    @Test
    void everyVertexHeaderNameCarriesAReservedPrefix() {
        assertEquals(List.of(), unprefixed(declared(header(VERTEX))), VERTEX);
    }

    @Test
    void everyBeamFragmentHeaderNameCarriesAReservedPrefix() {
        assertEquals(List.of(), unprefixed(declared(header(BEAM_FRAGMENT))), BEAM_FRAGMENT);
    }

    @Test
    void everyBeamVertexHeaderNameCarriesAReservedPrefix() {
        List<String> vertex = declared(header(BEAM_VERTEX));

        assertEquals(List.of(), unprefixed(vertex), BEAM_VERTEX);
        assertTrue(vertex.containsAll(List.of("EminusBeamVertex", "eminus_beam_vertex", "EminusVertex",
                "eminus_vertexPosition")), vertex.toString());
    }

    @Test
    void theScanReachesEveryKindOfHeaderName() {
        List<String> fragment = declared(header(FRAGMENT));
        List<String> vertex = declared(header(VERTEX));

        assertTrue(fragment.containsAll(List.of("EminusFrame", "EminusProjView", "EminusFragment", "eminus_fragment",
                "eminus_surface", "eminus_cell")), fragment.toString());
        assertTrue(vertex.containsAll(List.of("EminusQuadVertex", "eminus_quad_vertex", "eminus_vertex",
                "eminus_faceUV", "eminus_vertexPosition", "iris_TexId")), vertex.toString());
    }

    @Test
    void aNameWithoutAPrefixIsCaughtAndALocalNameIsNot() {
        String source = """
                layout(std140) uniform EminusFrame {
                    mat4 EminusView;
                    int Bare;
                };
                uniform sampler2D Plain;
                const float LOOSE = 1.0e-6, EMINUS_SECOND = 2.0;
                const mat4 eminus_pair[2] = mat4[2](mat4(1.0), mat4(2.0));
                struct EminusThing { int inner; };
                vec4 prototype(int argument);
                void helper(int argument) { int local = argument; }
                """;

        assertEquals(List.of("Bare", "Plain", "LOOSE", "prototype", "helper"), unprefixed(declared(source)));
    }

    private static List<String> unprefixed(List<String> names) {
        return names.stream()
                .filter(name -> !ENTRY_POINTS.contains(name) && PREFIXES.stream().noneMatch(name::startsWith))
                .toList();
    }

    private static List<String> declared(String source) {
        List<String> tokens = tokens(source);
        List<String> names = new ArrayList<>();
        List<String> statement = new ArrayList<>();
        int index = 0;
        while (index < tokens.size()) {
            String token = tokens.get(index);
            if (token.equals(END)) {
                names.addAll(statementNames(statement));
                statement.clear();
                index++;
            } else if (token.equals(OPEN_BRACE)) {
                int close = closing(tokens, index);
                List<String> head = withoutLayout(statement);
                if (head.contains(STRUCT)) {
                    names.add(head.get(head.indexOf(STRUCT) + 1));
                } else if (head.contains(OPEN_PAREN)) {
                    names.add(head.get(head.indexOf(OPEN_PAREN) - 1));
                } else {
                    names.add(head.getLast());
                    names.addAll(declared(String.join(LINE, tokens.subList(index + 1, close))));
                }
                statement.clear();
                index = close + 1;
            } else {
                statement.add(token);
                index++;
            }
        }

        return names;
    }

    private static List<String> statementNames(List<String> statement) {
        List<String> head = withoutLayout(statement);
        if (head.isEmpty()) {
            return List.of();
        }

        int call = head.indexOf(OPEN_PAREN);
        int assign = head.indexOf(ASSIGN);
        if (call >= 0 && (assign < 0 || call < assign)) {
            return List.of(head.get(call - 1));
        }

        List<String> names = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index <= head.size(); index++) {
            String token = index < head.size() ? head.get(index) : SEPARATOR;
            if (token.equals(OPEN_PAREN) || token.equals(OPEN_BRACKET)) {
                depth++;
            } else if (token.equals(CLOSE_PAREN) || token.equals(CLOSE_BRACKET)) {
                depth--;
            } else if (token.equals(SEPARATOR) && depth == 0) {
                names.add(variableName(head.subList(start, index)));
                start = index + 1;
            }
        }
        return names;
    }

    private static String variableName(List<String> declaration) {
        for (int index = 0; index < declaration.size(); index++) {
            String token = declaration.get(index);
            if (token.equals(ASSIGN) || token.equals(OPEN_BRACKET)) {
                return declaration.get(index - 1);
            }
        }
        return declaration.getLast();
    }

    private static List<String> withoutLayout(List<String> statement) {
        if (statement.isEmpty() || !statement.getFirst().equals(LAYOUT)) {
            return statement;
        }

        int depth = 0;
        for (int index = 1; index < statement.size(); index++) {
            String token = statement.get(index);
            if (token.equals(OPEN_PAREN)) {
                depth++;
            } else if (token.equals(CLOSE_PAREN) && --depth == 0) {
                return statement.subList(index + 1, statement.size());
            }
        }
        return List.of();
    }

    private static int closing(List<String> tokens, int open) {
        int depth = 0;
        for (int index = open; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (token.equals(OPEN_BRACE)) {
                depth++;
            } else if (token.equals(CLOSE_BRACE) && --depth == 0) {
                return index;
            }
        }
        throw new IllegalStateException("Unbalanced brace at token " + open);
    }

    private static List<String> tokens(String source) {
        StringBuilder code = new StringBuilder();
        for (String line : COMMENT.matcher(source).replaceAll(" ").split(LINE, -1)) {
            if (!line.strip().startsWith(DIRECTIVE)) {
                code.append(line).append(LINE);
            }
        }

        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(code);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static String header(String shader) {
        return GlslSource.compose(read(SHADERS + shader).orElseThrow(), List.of(), PackHeaderNamesTest::include);
    }

    private static Optional<String> include(Location location) {
        return read(ASSETS + location.namespace() + INCLUDES + location.path());
    }

    private static Optional<String> read(String resource) {
        try (InputStream stream = PackHeaderNamesTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
