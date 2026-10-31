package com.eminus.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class CompatGatesTest {
    private static final String REPO_ROOT_PROPERTY = "eminus.repoRoot";
    private static final String RESOURCES = "common/src/main/resources";
    private static final String COMPAT_CONFIGS = "eminus.compat.*.mixins.json";
    private static final String PLUGIN_KEY = "plugin";
    private static final String PACKAGE_KEY = "package";
    private static final List<String> MIXIN_LIST_KEYS = List.of("mixins", "client", "server");
    private static final String COMPAT_PACKAGE = "com/eminus/compat/";
    private static final String SHIPPED_CLASS = "ModPresence.class";
    private static final String CLASS_SUFFIX = ".class";

    static List<Path> configs() throws IOException {
        List<Path> configs = new ArrayList<>();
        try (DirectoryStream<Path> found = Files.newDirectoryStream(
                Path.of(System.getProperty(REPO_ROOT_PROPERTY)).resolve(RESOURCES), COMPAT_CONFIGS)) {
            found.forEach(configs::add);
        }

        assertTrue(!configs.isEmpty(), "no compat config found under " + RESOURCES);
        return configs;
    }

    @ParameterizedTest
    @MethodSource("configs")
    void everyMixinOfTheConfigHasExactlyOneGate(Path config) throws Exception {
        JsonObject json = read(config);
        ModPresenceGatePlugin plugin = pluginOf(json);
        String mixinPackage = json.get(PACKAGE_KEY).getAsString();

        for (String mixin : mixinsOf(json)) {
            String mixinClass = mixinPackage + "." + mixin;
            long gates = plugin.gates().stream().filter(gate -> gate.covers(mixinClass)).count();
            assertEquals(1, gates, config.getFileName() + ": " + mixin + " is covered by " + gates + " gates");
        }
    }

    @ParameterizedTest
    @MethodSource("configs")
    void everyGateAdmitsTheModBuildTheSuiteCarries(Path config) throws Exception {
        for (ModPresence gate : pluginOf(read(config)).gates()) {
            assertTrue(gate.admitted(), config.getFileName() + ": a gate refuses the build this suite runs against; "
                    + "the refusal line in the test log names the mixin and the member");
        }
    }

    @Test
    void theClassIndexListsEveryCompiledCompatClass() throws Exception {
        assertEquals(compiledCompatClasses(), indexedClasses(),
                "the jar's class index and the compiled compat package hold the same classes");
    }

    @Test
    void everyClassOfAGatePackageBelongsToExactlyOneGate() throws Exception {
        List<ModPresence> gates = new ArrayList<>();
        for (Path config : configs()) {
            gates.addAll(pluginOf(read(config)).gates());
        }

        for (String listed : indexedClasses()) {
            if (listed.substring(COMPAT_PACKAGE.length()).indexOf('/') < 0) {
                continue;
            }

            String className = listed.replace('/', '.');
            long owners = gates.stream().filter(gate -> gate.ownedClasses().contains(className)).count();
            assertEquals(1, owners, className + " belongs to " + owners + " gates");
        }
    }

    private static List<String> compiledCompatClasses() throws Exception {
        Path compat = Path.of(Objects.requireNonNull(ModPresence.class.getResource(SHIPPED_CLASS)).toURI())
                .getParent();
        Path root = classesRoot(compat);
        try (Stream<Path> files = Files.walk(compat)) {
            return files.map(file -> root.relativize(file).toString().replace('\\', '/'))
                    .filter(name -> name.endsWith(CLASS_SUFFIX))
                    .map(name -> name.substring(0, name.length() - CLASS_SUFFIX.length()))
                    .sorted()
                    .toList();
        }
    }

    private static Path classesRoot(Path compat) {
        int depth = Path.of(COMPAT_PACKAGE).getNameCount();
        return compat.getRoot().resolve(compat.subpath(0, compat.getNameCount() - depth));
    }

    private static List<String> indexedClasses() throws IOException {
        try (InputStream index = ModPresence.class.getClassLoader().getResourceAsStream(ModPresence.CLASS_INDEX)) {
            assertNotNull(index, ModPresence.CLASS_INDEX + " is not in common's main output");
            return new String(index.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank())
                    .sorted()
                    .toList();
        }
    }

    private static ModPresenceGatePlugin pluginOf(JsonObject config) throws ReflectiveOperationException {
        return Class.forName(config.get(PLUGIN_KEY).getAsString()).asSubclass(ModPresenceGatePlugin.class)
                .getDeclaredConstructor().newInstance();
    }

    private static List<String> mixinsOf(JsonObject config) {
        List<String> mixins = new ArrayList<>();
        for (String key : MIXIN_LIST_KEYS) {
            if (config.get(key) instanceof JsonArray list) {
                for (JsonElement mixin : list) {
                    mixins.add(mixin.getAsString());
                }
            }
        }

        return mixins;
    }

    private static JsonObject read(Path config) throws IOException {
        try (Reader reader = Files.newBufferedReader(config)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
