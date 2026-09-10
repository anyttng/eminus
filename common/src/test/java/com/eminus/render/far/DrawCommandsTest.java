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
    private static final int UP_QUADS = 5;
    private static final int DOWN_QUADS = 7;
    private static final double FAR_ABOVE = 4096.0;
    private static final double FAR_BELOW = -4096.0;
    private static final double INSIDE = 16.0;

    private final CellFrame frame = new CellFrame(0);
    private final DrawCommands commands = new DrawCommands(CAPACITY);
    private final long key = CellKey.pack(LEVEL, 0, 0, 0);

    @Test
    void aVisibleGroupBecomesOneCommandOverItsOwnQuadRange() {
        write(slots(slot(key, BLOCK)), FAR_ABOVE);

        assertEquals(1, commands.count());
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

        assertEquals(1, commands.count());
        assertEquals(DOWN_QUADS, commands.quads());
    }

    @Test
    void bothGroupsAreDrawnWhileTheCameraIsInsideTheCell() {
        write(slots(slot(key, BLOCK)), INSIDE);

        assertEquals(2, commands.count());
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
        int[] groupCount = new int[QuadGroups.COUNT];
        groupCount[QuadGroups.TRANSLUCENT] = UP_QUADS;
        MeshSlot held = new MeshSlot(key, BLOCK, UP_QUADS, new int[QuadGroups.COUNT], groupCount);

        write(slots(held), INSIDE);

        assertEquals(0, commands.count());
    }

    @Test
    void theSurplusPastTheCapacityIsDroppedForTheFrame() {
        int[] groupStart = new int[QuadGroups.COUNT];
        int[] groupCount = new int[QuadGroups.COUNT];
        for (int group = 0; group < QuadGroups.FACE_COUNT; group++) {
            groupStart[group] = group * UP_QUADS;
            groupCount[group] = UP_QUADS;
        }

        write(slots(new MeshSlot(key, BLOCK, QuadGroups.FACE_COUNT * UP_QUADS, groupStart, groupCount)), INSIDE);

        assertEquals(CAPACITY, commands.count());
        assertEquals(QuadGroups.FACE_COUNT - CAPACITY, commands.dropped());
    }

    private void write(MeshSlots slots, double cameraY) {
        commands.write(new RenderList(List.of(mesh(key))), slots, frame, INSIDE, cameraY, INSIDE);
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
        return new MeshSlot(key, block, DOWN_QUADS + UP_QUADS, groupStart, groupCount);
    }

    private static CellMesh mesh(long key) {
        return new CellMesh(key, new long[0], new int[QuadGroups.COUNT], new int[QuadGroups.COUNT]);
    }
}
