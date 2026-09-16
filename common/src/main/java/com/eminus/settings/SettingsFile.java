package com.eminus.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.eminus.Eminus;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public final class SettingsFile {
    public static final String INGESTION_KEY = "ingestion";
    public static final String LOWEST_STORED_LEVEL_KEY = "lowest_stored_level";
    public static final String FAR_RENDER_CELLS_KEY = "far_render_cells";
    public static final String WORKER_THREADS_KEY = "worker_threads";
    public static final String SUBDIVISION_SIZE_KEY = "subdivision_size";
    public static final String FOG_KEY = "fog";

    private static final String LOWEST_STORED_LEVEL_COMMENT = """
            Finest detail level kept on disk, 0..4: one voxel covers 2^level blocks
            (0 = 1 block, 1 = 2, 2 = 4, 3 = 8, 4 = 16). 0 keeps single blocks such as
            flowers visible at the far layer's nearest edge; 1 drops that level and
            cuts the store to about a seventh.""";

    private static final String FAR_RENDER_CELLS_COMMENT = """
            Radius of the far layer, in top-level cells of 512 blocks (32 chunks each):
            16 cells reach 8192 blocks, or 512 chunks. Work grows with the square of this
            number — more roots in the ring, more nodes walked per frame, more meshes in
            video memory.""";

    private static final String WORKER_THREADS_COMMENT = """
            Background worker threads. Written once from this machine as cores / 1.5,
            at least 1 and at most 8, so this number is yours rather than a universal
            default, and a config copied to another machine keeps it. Higher catches
            far terrain up faster and competes harder with the game's own chunk
            builders; lower is the other way round.""";

    private static final String SUBDIVISION_SIZE_COMMENT = """
            How large a node may look on screen, in pixels, before it is replaced by its
            eight finer children: this is where one detail level hands over to the next.
            Measured in screen pixels, so a higher resolution subdivides deeper at the
            same number. Smaller brings detail closer and costs more.""";

    private static final String FOG_COMMENT = """
            Fog over the far layer, carried on from the game's own fog at the edge of
            the loaded chunks and full at the far render distance. false also clears the
            game's open-air fog from the loaded chunks, so the two meet without a step.
            Fog that ends inside the loaded chunks (the Nether, a boss, blindness) and
            fog in water, lava or powder snow stay either way, and so does the fade of
            the far layer's outer edge.""";

    private static final Gson GSON = new Gson();
    private static final String INDENT = "  ";

    public static Settings load(Path file) {
        Settings defaults = Settings.defaults();
        if (!Files.isRegularFile(file)) {
            save(file, defaults);
            return defaults;
        }

        JsonObject json = read(file);
        if (json == null) {
            return defaults;
        }

        return new Settings(
                bool(json, INGESTION_KEY, defaults.ingestion()),
                bounded(json, LOWEST_STORED_LEVEL_KEY, defaults.lowestStoredLevel(),
                        Settings.MIN_DETAIL_LEVEL, Settings.MAX_DETAIL_LEVEL),
                bounded(json, FAR_RENDER_CELLS_KEY, defaults.farRenderCells(),
                        Settings.MIN_FAR_RENDER_CELLS, Settings.MAX_FAR_RENDER_CELLS),
                bounded(json, WORKER_THREADS_KEY, defaults.workerThreads(),
                        Settings.MIN_WORKER_THREADS, Settings.MAX_WORKER_THREADS),
                bounded(json, SUBDIVISION_SIZE_KEY, defaults.subdivisionSize(),
                        Settings.MIN_SUBDIVISION_SIZE, Settings.MAX_SUBDIVISION_SIZE),
                bool(json, FOG_KEY, defaults.fog()));
    }

    public static void save(Path file, Settings settings) {
        List<String> entries = new ArrayList<>();
        entries.add(entry(INGESTION_KEY, new JsonPrimitive(settings.ingestion()), null));
        entries.add(entry(LOWEST_STORED_LEVEL_KEY, new JsonPrimitive(settings.lowestStoredLevel()),
                LOWEST_STORED_LEVEL_COMMENT));
        entries.add(entry(FAR_RENDER_CELLS_KEY, new JsonPrimitive(settings.farRenderCells()),
                FAR_RENDER_CELLS_COMMENT));
        entries.add(entry(WORKER_THREADS_KEY, new JsonPrimitive(settings.workerThreads()),
                WORKER_THREADS_COMMENT));
        entries.add(entry(SUBDIVISION_SIZE_KEY, new JsonPrimitive(settings.subdivisionSize()),
                SUBDIVISION_SIZE_COMMENT));
        entries.add(entry(FOG_KEY, new JsonPrimitive(settings.fog()), FOG_COMMENT));

        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(file, "{\n" + String.join(",\n", entries) + "\n}\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            Eminus.LOGGER.error("Could not write the settings file {}", file, e);
        }
    }

    private static String entry(String key, JsonPrimitive value, String comment) {
        StringBuilder line = new StringBuilder();
        if (comment != null) {
            comment.lines().forEach(text -> line.append(INDENT).append("// ").append(text).append('\n'));
        }

        return line.append(INDENT)
                .append(GSON.toJson(new JsonPrimitive(key)))
                .append(": ")
                .append(GSON.toJson(value))
                .toString();
    }

    private static JsonObject read(Path file) {
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }

            Eminus.LOGGER.warn("The settings file {} is not a JSON object; every setting falls back to its default", file);
        } catch (IOException | RuntimeException e) {
            Eminus.LOGGER.warn("Could not read the settings file {}; every setting falls back to its default", file, e);
        }

        return null;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement value = json.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }

        return fellBack(key, value, fallback);
    }

    private static int bounded(JsonObject json, String key, int fallback, int min, int max) {
        JsonElement value = json.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            int number = value.getAsInt();
            if (number >= min && number <= max) {
                return number;
            }
        }

        return fellBack(key, value, fallback);
    }

    private static <T> T fellBack(String key, JsonElement value, T fallback) {
        Eminus.LOGGER.warn("Setting {} is {}; falling back to {}", key, value == null ? "missing" : value, fallback);
        return fallback;
    }

    private SettingsFile() {
    }
}
