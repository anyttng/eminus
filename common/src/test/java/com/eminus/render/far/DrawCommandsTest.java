package com.eminus.render.far;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.IntBuffer;
import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.QuadGroups;
import com.eminus.render.arena.ArenaAllocator;
import com.eminus.render.arena.MeshSlot;
import com.eminus.render.arena.MeshSlots;
import com.eminus.render.tree.RenderList;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

class DrawCommandsTest {
    private static final int CAPACITY = 4;
    private static final int LEVEL = 0;
    private static final int BLOCK = 3;
    private static final int NO_COLOURS = 0;
    private static final int FAR_BLOCK = 9;
    private static final int UP_QUADS = 5;
    private static final int DOWN_QUADS = 7;
    private static final int WATER_QUADS = 2;
    private static final int WATER_START = 12;
    private static final double FAR_ABOVE = 4096.0;
    private static final double FAR_BELOW = -4096.0;
    private static final double INSIDE = 16.0;

    private final CellFrame frame = new CellFrame(0);
    private final DrawCommands commands = new DrawCommands(CAPACITY);
    private final long key = CellKey.pack(LEVEL, 0, 0, 0);
    private final long farKey = CellKey.pack(LEVEL, 4, 0, 0);

    @Test
    void aVisibleGroupBecomesOneCommandOverItsOwnQuadRange() {
        write(slots(slot(key, BLOCK)), FAR_ABOVE);

        assertEquals(1, commands.opaqueCount());
        assertEquals(UP_QUADS, commands.quads());

        IntBuffer written = commands.buffer().asIntBuffer();
        assertEquals(UP_QUADS * DrawCommands.VERTICES_PER_QUAD, written.get(0));
        assertEquals(1, written.get(1));
        assertEquals((BLOCK * ArenaAllocator.QUADS_PER_BLOCK + DOWN_QUADS) * DrawCommands.VERTICES_PER_QUAD,
                written.get(2));
        assertEquals(0, written.get(3));
    }

    @Test
    void aGroupFacingAwayFromTheCameraIsNotDrawn() {
        write(slots(slot(key, BLOCK)), FAR_BELOW);

        assertEquals(1, commands.opaqueCount());
        assertEquals(DOWN_QUADS, commands.quads());
    }

    @Test
    void bothGroupsAreDrawnWhileTheCameraIsInsideTheCell() {
        write(slots(slot(key, BLOCK)), INSIDE);

        assertEquals(2, commands.opaqueCount());
        assertEquals(UP_QUADS + DOWN_QUADS, commands.quads());
    }

    @Test
    void aMeshTheArenaDoesNotHoldContributesNothing() {
        write(missing -> null, FAR_ABOVE);

        assertEquals(0, commands.count());
        assertEquals(0, commands.quads());
    }

    @Test
    void aTranslucentGroupIsLeftToItsOwnPass() {
        write(slots(water(key, BLOCK)), INSIDE);

        assertEquals(0, commands.count());
    }

    @Test
    void aTranslucentGroupBecomesOneCommandOverItsOwnQuadRange() {
        translucent(slots(water(key, BLOCK)), mesh(key));

        assertEquals(0, commands.opaqueCount());
        assertEquals(1, commands.translucentCount());
        assertEquals(WATER_QUADS, commands.quads());

        IntBuffer written = commands.buffer().asIntBuffer();
        assertEquals(WATER_QUADS * DrawCommands.VERTICES_PER_QUAD, written.get(0));
        assertEquals((BLOCK * ArenaAllocator.QUADS_PER_BLOCK + WATER_START) * DrawCommands.VERTICES_PER_QUAD,
                written.get(2));
    }

    @Test
    void theTranslucentCommandsKeepTheOrderTheyAreGivenIn() {
        MeshSlot near = water(key, BLOCK);
        MeshSlot far = water(farKey, FAR_BLOCK);

        translucent(wanted -> wanted == key ? near : wanted == farKey ? far : null, mesh(farKey), mesh(key));

        assertEquals(2, commands.translucentCount());

        IntBuffer written = commands.buffer().asIntBuffer();
        assertEquals((FAR_BLOCK * ArenaAllocator.QUADS_PER_BLOCK + WATER_START) * DrawCommands.VERTICES_PER_QUAD,
                written.get(2));
        assertEquals((BLOCK * ArenaAllocator.QUADS_PER_BLOCK + WATER_START) * DrawCommands.VERTICES_PER_QUAD,
                written.get(DrawCommands.COMMAND_INTS + 2));
    }

    @Test
    void theTranslucentCommandsSitAfterTheOpaqueOnes() {
        MeshSlot held = both(key, BLOCK);

        commands.write(new RenderList(List.of(mesh(key))), List.of(mesh(key)), slots(held), frame,
                INSIDE, INSIDE, INSIDE);

        assertEquals(2, commands.opaqueCount());
        assertEquals(1, commands.translucentCount());

        IntBuffer written = commands.buffer().asIntBuffer();
        assertEquals(WATER_QUADS * DrawCommands.VERTICES_PER_QUAD, written.get(2 * DrawCommands.COMMAND_INTS));
    }

    @Test
    void aMeshWithoutTranslucentQuadsAddsNoCommand() {
        translucent(slots(slot(key, BLOCK)), mesh(key));

        assertEquals(0, commands.count());
    }

    @Test
    void aTranslucentMeshTheArenaDoesNotHoldContributesNothing() {
        translucent(missing -> null, mesh(key));

        assertEquals(0, commands.count());
    }

    @Test
    void theSurplusPastTheCapacityIsDroppedForTheFrame() {
        int[] groupStart = new int[QuadGroups.COUNT];
        int[] groupCount = new int[QuadGroups.COUNT];
        for (int group = 0; group < QuadGroups.DIRECTIONAL_COUNT; group++) {
            groupStart[group] = group * UP_QUADS;
            groupCount[group] = UP_QUADS;
        }

        write(slots(new MeshSlot(key, BLOCK, QuadGroups.DIRECTIONAL_COUNT * UP_QUADS, NO_COLOURS, groupStart,
                groupCount)), INSIDE);

        assertEquals(CAPACITY, commands.opaqueCount());
        assertEquals(QuadGroups.DIRECTIONAL_COUNT - CAPACITY, commands.dropped());
    }

    private void write(MeshSlots slots, double cameraY) {
        commands.write(new RenderList(List.of(mesh(key))), List.of(), slots, frame, INSIDE, cameraY, INSIDE);
    }

    private void translucent(MeshSlots slots, CellMesh... ordered) {
        commands.write(RenderList.EMPTY, List.of(ordered), slots, frame, INSIDE, INSIDE, INSIDE);
    }

    private static MeshSlots slots(MeshSlot held) {
        return wanted -> wanted == held.key() ? held : null;
    }

    private static MeshSlot slot(long key, int block) {
        int[] groupStart = new int[QuadGroups.COUNT];
        int[] groupCount = new int[QuadGroups.COUNT];
        groupCount[Direction.DOWN.ordinal()] = DOWN_QUADS;
        groupStart[Direction.UP.ordinal()] = DOWN_QUADS;
        groupCount[Direction.UP.ordinal()] = UP_QUADS;
        return new MeshSlot(key, block, DOWN_QUADS + UP_QUADS, NO_COLOURS, groupStart, groupCount);
    }

    private static MeshSlot water(long key, int block) {
        int[] groupStart = new int[QuadGroups.COUNT];
        int[] groupCount = new int[QuadGroups.COUNT];
        groupStart[QuadGroups.TRANSLUCENT] = WATER_START;
        groupCount[QuadGroups.TRANSLUCENT] = WATER_QUADS;
        return new MeshSlot(key, block, WATER_START + WATER_QUADS, NO_COLOURS, groupStart, groupCount);
    }

    private static MeshSlot both(long key, int block) {
        MeshSlot opaque = slot(key, block);
        int[] groupStart = opaque.groupStart().clone();
        int[] groupCount = opaque.groupCount().clone();
        groupStart[QuadGroups.TRANSLUCENT] = WATER_START;
        groupCount[QuadGroups.TRANSLUCENT] = WATER_QUADS;
        return new MeshSlot(key, block, WATER_START + WATER_QUADS, NO_COLOURS, groupStart, groupCount);
    }

    private static CellMesh mesh(long key) {
        return CellMesh.empty(key);
    }
}
