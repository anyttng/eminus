package com.eminus.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eminus.VanillaBootstrap;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StateTableTest {
    private static final String DAMAGED_STATE = "nosuchmod:nosuchblock";
    private static final int DAMAGED_ID = 1;
    private static final int UNKNOWN_ID = 99;
    private static final int ID_PAST_CAPACITY = 1000;

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

        assertEquals(StateTable.FULL_OPACITY, stone.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        assertEquals(stone.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), table.opacity(table.idOf(stone)));
        assertEquals(glass.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), table.opacity(table.idOf(glass)));
    }

    @Test
    void leavesArePinnedToFullOpacityAboveTheirLightDampening() {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        StateTable table = new StateTable(ids);

        assertTrue(leaves.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) < StateTable.FULL_OPACITY);
        assertEquals(StateTable.FULL_OPACITY, table.opacity(table.idOf(leaves)));
    }

    @Test
    void theSeeThroughViewGivesLeavesTheirLightDampeningAndLeavesTheRestAlone() {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        StateTable table = new StateTable(ids);

        assertEquals(leaves.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), table.seeThroughLeaves().opacity(table.idOf(leaves)));
        assertEquals(StateTable.FULL_OPACITY, table.seeThroughLeaves().opacity(table.idOf(stone)));
    }

    @Test
    void snowLayersAndCarpetsAreCoversAndFullOrRaisedBlocksAreNot() {
        StateTable table = new StateTable(ids);

        assertTrue(table.cover(table.idOf(Blocks.SNOW.defaultBlockState())));
        assertTrue(table.cover(table.idOf(Blocks.WHITE_CARPET.defaultBlockState())));
        assertTrue(table.cover(table.idOf(Blocks.MOSS_CARPET.defaultBlockState())));
        assertFalse(table.cover(table.idOf(
                Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, SnowLayerBlock.MAX_HEIGHT))));
        assertFalse(table.cover(table.idOf(Blocks.STONE.defaultBlockState())));
        assertFalse(table.cover(table.idOf(Blocks.STONE_SLAB.defaultBlockState())));
    }

    @Test
    void aStoredStateComesBackAsItself() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        ids.load(VoxelEntry.AIR_STATE_ID, BlockStateParser.serialize(Blocks.AIR.defaultBlockState()));
        ids.load(DAMAGED_ID, BlockStateParser.serialize(stone));

        assertSame(stone, new StateTable(ids).state(DAMAGED_ID));
    }

    @Test
    void aStoredStatePastTheInitialCapacityComesBackWithItsOpacity() {
        BlockState glass = Blocks.GLASS.defaultBlockState();
        ids.load(VoxelEntry.AIR_STATE_ID, BlockStateParser.serialize(Blocks.AIR.defaultBlockState()));
        ids.load(ID_PAST_CAPACITY, BlockStateParser.serialize(glass));
        StateTable table = new StateTable(ids);

        assertSame(glass, table.state(ID_PAST_CAPACITY));
        assertEquals(glass.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), table.opacity(ID_PAST_CAPACITY));
        assertEquals(glass.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), table.seeThroughLeaves().opacity(ID_PAST_CAPACITY));
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
