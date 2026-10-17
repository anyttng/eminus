package com.eminus.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SettingsDraftTest {
    private final List<Settings> writes = new ArrayList<>();
    private Settings saved = Settings.defaults();

    private SettingsDraft draft() {
        return new SettingsDraft(() -> saved, settings -> {
            writes.add(settings);
            saved = settings;
        });
    }

    @Test
    void anEditStaysInTheDraft() {
        SettingsDraft draft = draft();

        draft.edit(settings -> settings.withFog(false).withLodChoice(LodChoice.LOW));

        assertTrue(writes.isEmpty());
        assertTrue(draft.changed());
        assertEquals(Settings.defaults(), saved);
    }

    @Test
    void severalEditsLandAsOneWrite() {
        SettingsDraft draft = draft();

        draft.edit(settings -> settings.withFog(false));
        draft.edit(settings -> settings.withLodChoice(LodChoice.LOW));
        draft.write();

        assertEquals(List.of(Settings.defaults().withFog(false).withLodChoice(LodChoice.LOW)), writes);
        assertFalse(draft.changed());
    }

    @Test
    void anUnchangedDraftWritesNothing() {
        SettingsDraft draft = draft();

        draft.edit(settings -> settings.withFog(false));
        draft.edit(settings -> settings.withFog(true));
        draft.write();

        assertFalse(draft.changed());
        assertTrue(writes.isEmpty());
    }
}
