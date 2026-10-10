package com.eminus.render.tree;

import java.util.List;

import com.eminus.cell.CellKey;
import com.eminus.mesh.MeshSummary;

public final class NodeRow {
    public static final int LEVEL = 0;
    public static final int CELL_X = 1;
    public static final int CELL_Y = 2;
    public static final int CELL_Z = 3;
    public static final int SLOT_PRESENT = 4;
    public static final int SLOT_QUADS = 5;
    public static final int SLOT_BLOCK = 6;
    public static final int NODE_PRESENT = 7;
    public static final int MESHED = 8;
    public static final int MESH_QUADS = 9;
    public static final int OCCUPANCY = 10;
    public static final int BUILDING = 11;
    public static final int REQUESTED = 12;
    public static final int CHILDREN_READY = 13;
    public static final int LAST_SEEN = 14;
    public static final int WALKS = 15;
    public static final int WIDTH = 16;

    private NodeRow() {
    }

    static List<long[]> describe(NodeTable nodes, List<long[]> rows, long walks) {
        for (long[] row : rows) {
            TreeNode node = nodes.get(CellKey.pack((int) row[LEVEL], (int) row[CELL_X], (int) row[CELL_Y],
                    (int) row[CELL_Z]));
            MeshSummary mesh = node == null ? null : node.mesh();
            row[NODE_PRESENT] = node == null ? 0 : 1;
            row[MESHED] = mesh == null ? 0 : 1;
            row[MESH_QUADS] = mesh == null ? 0 : mesh.quadCount();
            row[OCCUPANCY] = node == null ? 0 : node.occupancy();
            row[BUILDING] = node != null && node.building() ? 1 : 0;
            row[REQUESTED] = node == null ? 0 : node.requestedOctants();
            row[CHILDREN_READY] = node != null && node.childrenReady() ? 1 : 0;
            row[LAST_SEEN] = node == null ? 0 : node.lastSeen();
            row[WALKS] = walks;
        }

        return rows;
    }
}
