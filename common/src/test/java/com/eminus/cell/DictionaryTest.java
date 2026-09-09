package com.eminus.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class DictionaryTest {
    @Test
    void theFirstRegisteredValueGetsIdZeroAndEveryIdIsStable() {
        Dictionary<String> dictionary = new Dictionary<>((id, value) -> {
        });

        assertEquals(0, dictionary.register("air"));
        assertEquals(1, dictionary.register("stone"));
        assertEquals(0, dictionary.register("air"));
        assertEquals("stone", dictionary.value(1));
        assertEquals(1, dictionary.id("stone"));
        assertEquals(2, dictionary.size());
    }

    @Test
    void anUnknownValueAndAnUnknownIdReadAsMissing() {
        Dictionary<String> dictionary = new Dictionary<>((id, value) -> {
        });

        assertEquals(Dictionary.MISSING, dictionary.id("stone"));
        assertNull(dictionary.value(0));
        assertNull(dictionary.value(-1));
    }

    @Test
    void theCallbackRunsBeforeTheIdIsVisible() {
        List<String> calls = new ArrayList<>();
        AtomicReference<Dictionary<String>> holder = new AtomicReference<>();
        Dictionary<String> dictionary = new Dictionary<>((id, value) -> {
            calls.add(id + " " + value);
            assertNull(holder.get().value(id));
        });
        holder.set(dictionary);

        assertEquals(0, dictionary.register("air"));
        assertEquals(List.of("0 air"), calls);
        assertEquals("air", dictionary.value(0));
    }

    @Test
    void aRepeatedRegistrationDoesNotCallTheCallbackAgain() {
        List<Integer> ids = new ArrayList<>();
        Dictionary<String> dictionary = new Dictionary<>((id, value) -> ids.add(id));

        dictionary.register("air");
        dictionary.register("air");
        dictionary.register("stone");

        assertEquals(List.of(0, 1), ids);
    }

    @Test
    void aLoadedEntryIsNotPersistedAgain() {
        List<Integer> ids = new ArrayList<>();
        Dictionary<String> dictionary = new Dictionary<>((id, value) -> ids.add(id));

        dictionary.load(0, "air");
        dictionary.load(1, "stone");

        assertEquals(List.of(), ids);
        assertEquals(0, dictionary.register("air"));
        assertEquals(2, dictionary.register("dirt"));
        assertEquals(List.of(2), ids);
    }

    @Test
    void aSparseLoadGrowsTheTable() {
        Dictionary<String> dictionary = new Dictionary<>((id, value) -> {
        });

        dictionary.load(1000, "stone");

        assertEquals("stone", dictionary.value(1000));
        assertEquals(1001, dictionary.size());
    }
}
