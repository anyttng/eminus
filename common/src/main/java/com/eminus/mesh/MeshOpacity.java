package com.eminus.mesh;

import com.eminus.cell.DetailLevel;
import com.eminus.cell.StateOpacity;
import com.eminus.cell.StateTable;

@FunctionalInterface
public interface MeshOpacity {
    StateOpacity at(int level);

    static MeshOpacity of(StateTable states, boolean seeThroughLeaves) {
        StateOpacity nearest = seeThroughLeaves ? states.seeThroughLeaves() : states;
        return level -> level == DetailLevel.MIN ? nearest : states;
    }
}
