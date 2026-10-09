package com.eminus.cell;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.eminus.Eminus;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class StateTable implements StateOpacity {
    public static final String DICTIONARY_NAME = "state";
    public static final int FULL_OPACITY = 15;
    public static final int CLEAR_OPACITY = 0;
    public static final BlockState PLACEHOLDER = Blocks.CONCRETE.pick(DyeColor.MAGENTA).defaultBlockState();

    private static final int INITIAL_CAPACITY = 256;
    private static final int UNKNOWN_OPACITY = -1;

    private final Dictionary<String> ids;
    private final Map<BlockState, Integer> byState = new ConcurrentHashMap<>();
    private final StateOpacity seeThroughLeaves = this::lightDampening;

    private final IdTable opacities = new IdTable(INITIAL_CAPACITY, UNKNOWN_OPACITY);
    private final IdTable dampenings = new IdTable(INITIAL_CAPACITY, UNKNOWN_OPACITY);

    private volatile BlockState[] states = new BlockState[INITIAL_CAPACITY];

    public StateTable(Dictionary<String> ids) {
        this.ids = ids;

        BlockState air = Blocks.AIR.defaultBlockState();
        int id = ids.register(BlockStateParser.serialize(air));
        if (id != VoxelEntry.AIR_STATE_ID) {
            throw new IllegalStateException("The state dictionary holds " + ids.value(VoxelEntry.AIR_STATE_ID)
                    + " at id " + VoxelEntry.AIR_STATE_ID + ", where air must be.");
        }

        remember(id, air);
        byState.put(air, id);
    }

    public int idOf(BlockState state) {
        if (state.isAir()) {
            return VoxelEntry.AIR_STATE_ID;
        }

        Integer known = byState.get(state);
        return known == null ? register(state) : known;
    }

    public BlockState state(int id) {
        BlockState[] snapshot = states;
        BlockState known = id >= 0 && id < snapshot.length ? snapshot[id] : null;
        return known == null ? resolve(id) : known;
    }

    @Override
    public int opacity(int stateId) {
        int known = opacities.get(stateId);
        return known == UNKNOWN_OPACITY ? opacityOf(resolve(stateId)) : known;
    }

    @Override
    public boolean cover(int stateId) {
        Block block = state(stateId).getBlock();
        return (block instanceof SnowLayerBlock || block instanceof CarpetBlock) && opacity(stateId) == CLEAR_OPACITY;
    }

    public StateOpacity seeThroughLeaves() {
        return seeThroughLeaves;
    }

    public int size() {
        return ids.size();
    }

    private int lightDampening(int stateId) {
        int known = dampenings.get(stateId);
        return known == UNKNOWN_OPACITY ? resolve(stateId).getLightDampening() : known;
    }

    private int register(BlockState state) {
        int id = ids.register(BlockStateParser.serialize(state));
        remember(id, state);
        byState.put(state, id);
        return id;
    }

    private synchronized BlockState resolve(int id) {
        if (id < 0) {
            return PLACEHOLDER;
        }

        BlockState[] snapshot = states;
        if (id < snapshot.length && snapshot[id] != null) {
            return snapshot[id];
        }

        BlockState decoded = decode(ids.value(id));
        remember(id, decoded);
        return decoded;
    }

    private synchronized void remember(int id, BlockState state) {
        BlockState[] current = states;
        if (id >= current.length) {
            current = Arrays.copyOf(current, IdTable.grownLength(current.length, id));
        }

        current[id] = state;
        states = current;
        opacities.put(id, opacityOf(state));
        dampenings.put(id, state.getLightDampening());
    }

    private static BlockState decode(String value) {
        if (value == null) {
            return PLACEHOLDER;
        }

        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, value, false).blockState();
        } catch (CommandSyntaxException | RuntimeException failure) {
            Eminus.LOGGER.warn("The stored block state {} does not resolve; the placeholder stands in.", value);
            return PLACEHOLDER;
        }
    }

    private static int opacityOf(BlockState state) {
        return state.getBlock() instanceof LeavesBlock ? FULL_OPACITY : state.getLightDampening();
    }
}
