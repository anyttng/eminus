package com.eminus.mesh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eminus.VanillaBootstrap;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.Dictionary;
import com.eminus.cell.StateTable;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MeshOpacityTest {
    private static final int SEE_THROUGH_DAMPENING = 1;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    private final StateTable states = new StateTable(new Dictionary<>((id, value) -> { }));
    private final BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();

    @Test
    void seeThroughLeavesOpenOnlyTheFinestLevel() {
        MeshOpacity opacity = MeshOpacity.of(states, true);
        int id = states.idOf(leaves);

        assertEquals(SEE_THROUGH_DAMPENING, opacity.at(DetailLevel.MIN).opacity(id));
        assertEquals(StateTable.FULL_OPACITY, opacity.at(DetailLevel.MIN + 1).opacity(id));
        assertEquals(StateTable.FULL_OPACITY, opacity.at(DetailLevel.MAX).opacity(id));
    }

    @Test
    void opaqueLeavesStayPinnedOnEveryLevel() {
        MeshOpacity opacity = MeshOpacity.of(states, false);
        int id = states.idOf(leaves);

        for (int level = DetailLevel.MIN; level <= DetailLevel.MAX; level++) {
            assertEquals(StateTable.FULL_OPACITY, opacity.at(level).opacity(id));
        }
    }
}
