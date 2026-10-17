package com.eminus.settings;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class SettingsDraft {
    private final Supplier<Settings> saved;
    private final Consumer<Settings> writer;
    private Settings draft;

    public SettingsDraft(Supplier<Settings> saved, Consumer<Settings> writer) {
        this.saved = saved;
        this.writer = writer;
        this.draft = saved.get();
    }

    public static SettingsDraft of(SettingsService service) {
        return new SettingsDraft(service::settings, service::update);
    }

    public Settings settings() {
        return draft;
    }

    public void edit(UnaryOperator<Settings> edit) {
        draft = edit.apply(draft);
    }

    public boolean changed() {
        return !draft.equals(saved.get());
    }

    public void write() {
        if (changed()) {
            writer.accept(draft);
        }
    }
}
