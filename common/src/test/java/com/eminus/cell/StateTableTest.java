package com.eminus.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.eminus.VanillaBootstrap;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StateTableTest {
    private static final String DAMAGED_STATE = "nosuchmod:nosuchblock";
    private static final int DAMAGED_ID = 1;
    private static final int UNKNOWN_ID = 99;

    @BeforeAll
    static void bootstrapVanilla() {
        VanillaBootstrap.ensure();
    }

    private final Dictionary<String> ids = new Dictionary<>((id, value) -> { });

    @Test
    void everyAirStateTakesTheReservedFirstId() {
        StateTable table = new StateTable(ids);

        assertEquals(VoxelEntry.AIR_STATE_ID, table.idOf(Blocks.AIR.defaultBlockState()));
        assertEquals(VoxelEntry.AIR_STATE_ID, table.idOf(Blocks.CAVE_AIR.defaultBlockState()));
        assertEquals(VoxelEntry.AIR_STATE_ID, table.idOf(Blocks.VOID_AIR.defaultBlockState()));
    }

    @Test
    void opacityFollowsTheStatesLightDampening() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        StateTable table = new StateTable(ids);

        assertEquals(StateTable.FULL_OPACITY, stone.getLightDampening());
        assertEquals(stone.getLightDampening(), table.opacity(table.idOf(stone)));
        assertEquals(glass.getLightDampening(), table.opacity(table.idOf(glass)));
    }

    @Test
    void aStoredStateComesBackAsItself() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        ids.load(VoxelEntry.AIR_STATE_ID, BlockStateParser.serialize(Blocks.AIR.defaultBlockState()));
        ids.load(DAMAGED_ID, BlockStateParser.serialize(stone));

        assertSame(stone, new StateTable(ids).state(DAMAGED_ID));
    }

    @Test
    void aStateTheDictionaryCannotResolveGivesThePlaceholder() {
        ids.load(VoxelEntry.AIR_STATE_ID, BlockStateParser.serialize(Blocks.AIR.defaultBlockState()));
        ids.load(DAMAGED_ID, DAMAGED_STATE);
        StateTable table = new StateTable(ids);

        assertSame(StateTable.PLACEHOLDER, table.state(DAMAGED_ID));
        assertSame(StateTable.PLACEHOLDER, table.state(UNKNOWN_ID));
        assertNotEquals(Blocks.AIR.defaultBlockState(), StateTable.PLACEHOLDER);
    }
}
