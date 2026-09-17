package com.eminus.render.far;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.mesh.MeshSummary;
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
    private static final int GROWTH = 2;

    private final float[] bounds = new float[MeshSlot.BOUNDS];

    private int capacity;
    private ByteBuffer bytes;
    private IntBuffer commands;
    private int opaqueCount;
    private int translucentCount;
    private int quads;

    public DrawCommands(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("An indirect buffer of " + capacity + " commands draws nothing.");
        }

        allocate(capacity);
    }

    public int capacity() {
        return capacity;
    }

    public int opaqueCount() {
        return opaqueCount;
    }

    public int translucentCount() {
        return translucentCount;
    }

    public int count() {
        return opaqueCount + translucentCount;
    }

    public int quads() {
        return quads;
    }

    public ByteBuffer buffer() {
        return bytes.clear().limit(count() * COMMAND_BYTES);
    }

    public void write(RenderList list, List<MeshSummary> translucent, MeshSlots slots, CellFrame frame,
            double cameraX, double cameraY, double cameraZ) {
        commands.clear();
        opaqueCount = 0;
        translucentCount = 0;
        quads = 0;

        for (MeshSummary mesh : list.meshes()) {
            MeshSlot slot = slots.slot(mesh.key());
            if (slot != null) {
                writeGroups(slot, frame, cameraX, cameraY, cameraZ);
            }
        }

        for (MeshSummary mesh : translucent) {
            MeshSlot slot = slots.slot(mesh.key());
            if (slot != null && put(slot, QuadGroups.TRANSLUCENT)) {
                translucentCount++;
            }
        }
    }

    private void writeGroups(MeshSlot slot, CellFrame frame, double cameraX, double cameraY, double cameraZ) {
        slot.bounds(frame, bounds);

        for (int group = 0; group < QuadGroups.TRANSLUCENT; group++) {
            if (slot.groupCount(group) == 0 || !GroupFacing.visible(group, bounds, cameraX, cameraY, cameraZ)) {
                continue;
            }

            if (put(slot, group)) {
                opaqueCount++;
            }
        }
    }

    private boolean put(MeshSlot slot, int group) {
        int groupQuads = slot.groupCount(group);
        if (groupQuads == 0) {
            return false;
        }

        if (count() == capacity) {
            grow();
        }

        commands.put(groupQuads * VERTICES_PER_QUAD)
                .put(ONE_INSTANCE)
                .put((slot.baseQuad() + slot.groupStart(group)) * VERTICES_PER_QUAD)
                .put(FIRST_INSTANCE);
        quads += groupQuads;
        return true;
    }

    private void grow() {
        ByteBuffer written = bytes.clear().limit(count() * COMMAND_BYTES);
        allocate(capacity * GROWTH);
        bytes.put(written).clear();
        commands.position(count() * COMMAND_INTS);
    }

    private void allocate(int commandCapacity) {
        capacity = commandCapacity;
        bytes = ByteBuffer.allocateDirect(commandCapacity * COMMAND_BYTES).order(ByteOrder.nativeOrder());
        commands = bytes.asIntBuffer();
    }
}
