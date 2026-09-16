package com.eminus.settings;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class SettingsService {
    public static final String FILE_NAME = "eminus.json";

    private static SettingsService service;

    private final Path file;
    private final List<Consumer<Settings>> listeners = new CopyOnWriteArrayList<>();

    private volatile Settings settings;

    private SettingsService(Path file, Settings settings) {
        this.file = file;
        this.settings = settings;
    }

    public static SettingsService load(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        return new SettingsService(file, SettingsFile.load(file));
    }

    public static void set(SettingsService chosen) {
        service = chosen;
    }

    public static SettingsService get() {
        if (service == null) {
            throw new IllegalStateException("Settings are not loaded — the loader entrypoint must call SettingsService.set first.");
        }

        return service;
    }

    public Settings settings() {
        return settings;
    }

    public void addListener(Consumer<Settings> listener) {
        listeners.add(listener);
    }

    public void update(Settings updated) {
        if (updated.equals(settings)) {
            return;
        }

        settings = updated;
        SettingsFile.save(file, updated);
        listeners.forEach(listener -> listener.accept(updated));
    }
}
