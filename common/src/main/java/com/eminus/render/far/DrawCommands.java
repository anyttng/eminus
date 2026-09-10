package com.eminus.render.far;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import com.eminus.Eminus;
import com.eminus.cell.CellFrame;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.QuadGroups;
import com.eminus.render.arena.MeshSlot;
import com.eminus.render.arena.MeshSlots;
import com.eminus.render.tree.RenderList;

public final class DrawCommands {
    public static final int VERTICES_PER_QUAD = 6;
    public static final int COMMAND_INTS = 4;
    public static final int COMMAND_BYTES = COMMAND_INTS * Integer.BYTES;

    private static final int ONE_INSTANCE = 1;
    private static final int FIRST_INSTANCE = 0;

    private final int capacity;
    private final ByteBuffer bytes;
    private final IntBuffer commands;
    private final float[] bounds = new float[MeshSlot.BOUNDS];

    private int count;
    private int quads;
    private int dropped;

    public DrawCommands(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("An indirect buffer of " + capacity + " commands draws nothing.");
        }

        this.capacity = capacity;
        bytes = ByteBuffer.allocateDirect(capacity * COMMAND_BYTES).order(ByteOrder.nativeOrder());
        commands = bytes.asIntBuffer();
    }

    public int capacity() {
        return capacity;
    }

    public int count() {
        return count;
    }

    public int quads() {
        return quads;
    }

    public int dropped() {
        return dropped;
    }

    public ByteBuffer buffer() {
        return bytes.clear().limit(count * COMMAND_BYTES);
    }

    public void write(RenderList list, MeshSlots slots, CellFrame frame,
            double cameraX, double cameraY, double cameraZ) {
        commands.clear();
        count = 0;
        quads = 0;
        dropped = 0;

        for (CellMesh mesh : list.meshes()) {
            MeshSlot slot = slots.slot(mesh.key());
            if (slot != null) {
                writeMesh(slot, frame, cameraX, cameraY, cameraZ);
            }
        }

        if (dropped > 0) {
            Eminus.LOGGER.warn("The indirect buffer holds {} commands; {} quad groups are dropped for this frame",
                    capacity, dropped);
        }
    }

    private void writeMesh(MeshSlot slot, CellFrame frame, double cameraX, double cameraY, double cameraZ) {
        slot.bounds(frame, bounds);

        for (int group = 0; group < QuadGroups.TRANSLUCENT; group++) {
            int groupQuads = slot.groupCount(group);
            if (groupQuads == 0 || !GroupFacing.visible(group, bounds, cameraX, cameraY, cameraZ)) {
                continue;
            }

            if (count == capacity) {
                dropped++;
                continue;
            }

            commands.put(groupQuads * VERTICES_PER_QUAD)
                    .put(ONE_INSTANCE)
                    .put((slot.baseQuad() + slot.groupStart(group)) * VERTICES_PER_QUAD)
                    .put(FIRST_INSTANCE);
            count++;
            quads += groupQuads;
        }
    }
}
