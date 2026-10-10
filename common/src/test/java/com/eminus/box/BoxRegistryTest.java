package com.eminus.box;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.eminus.api.v1.FarBox;
import com.eminus.api.v1.FarBoxGroup;

import org.junit.jupiter.api.Test;

class BoxRegistryTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final FarBox RED = new FarBox(0.0, 64.0, 0.0, 8.0, 128.0, 8.0, 0xFFFF0000, false);
    private static final FarBox GREEN = new FarBox(-8.5, 64.0, -8.5, 0.5, 70.0, 0.5, 0xFF00FF00, true);
    private static final FarBox BLUE = new FarBox(100.0, 0.0, 100.0, 101.0, 1.0, 101.0, 0x800000FF, false);

    @Test
    void aRegisteredGroupIsInItsDimensionsSnapshot() {
        BoxRegistry registry = new BoxRegistry();
        registry.register(OVERWORLD, List.of(RED, GREEN));

        assertEquals(List.of(RED, GREEN), registry.snapshot(OVERWORLD).boxes());
    }

    @Test
    void aGroupOfAnotherDimensionIsNotInTheSnapshot() {
        BoxRegistry registry = new BoxRegistry();
        registry.register(NETHER, List.of(RED));
        registry.register(OVERWORLD, List.of(GREEN));

        assertEquals(List.of(GREEN), registry.snapshot(OVERWORLD).boxes());
        assertEquals(List.of(RED), registry.snapshot(NETHER).boxes());
    }

    @Test
    void groupsFollowTheirRegistrationOrder() {
        BoxRegistry registry = new BoxRegistry();
        registry.register(OVERWORLD, List.of(BLUE));
        registry.register(OVERWORLD, List.of(RED, GREEN));

        assertEquals(List.of(BLUE, RED, GREEN), registry.snapshot(OVERWORLD).boxes());
    }

    @Test
    void anUpdateReplacesTheGroupsBoxesAndMovesTheVersion() {
        BoxRegistry registry = new BoxRegistry();
        FarBoxGroup group = registry.register(OVERWORLD, List.of(RED));
        long registered = registry.version();

        group.update(List.of(BLUE));

        assertNotEquals(registered, registry.version());
        assertEquals(List.of(BLUE), registry.snapshot(OVERWORLD).boxes());
    }

    @Test
    void aRemovedGroupIsGoneAndIgnoresLaterCalls() {
        BoxRegistry registry = new BoxRegistry();
        FarBoxGroup group = registry.register(OVERWORLD, List.of(RED));
        group.remove();
        long removed = registry.version();

        group.update(List.of(GREEN));
        group.remove();

        assertTrue(registry.snapshot(OVERWORLD).boxes().isEmpty());
        assertEquals(removed, registry.version());
    }

    @Test
    void clearingDropsEveryGroupAndLeavesTheirHandlesInert() {
        BoxRegistry registry = new BoxRegistry();
        FarBoxGroup overworld = registry.register(OVERWORLD, List.of(RED));
        registry.register(NETHER, List.of(GREEN));
        registry.clear();

        overworld.update(List.of(BLUE));

        assertTrue(registry.snapshot(OVERWORLD).boxes().isEmpty());
        assertTrue(registry.snapshot(NETHER).boxes().isEmpty());
    }

    @Test
    void theSnapshotCarriesTheVersionItWasTakenAt() {
        BoxRegistry registry = new BoxRegistry();
        registry.register(OVERWORLD, List.of(RED));

        assertEquals(registry.version(), registry.snapshot(OVERWORLD).version());
    }

    @Test
    void aBoxWhoseMinimumExceedsItsMaximumIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new FarBox(1.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0xFFFFFFFF, false));
        assertThrows(IllegalArgumentException.class,
                () -> new FarBox(Double.NaN, 0.0, 0.0, 1.0, 1.0, 1.0, 0xFFFFFFFF, false));
        assertThrows(IllegalArgumentException.class,
                () -> new FarBox(0.0, 0.0, 0.0, Double.POSITIVE_INFINITY, 1.0, 1.0, 0xFFFFFFFF, false));
    }
}
