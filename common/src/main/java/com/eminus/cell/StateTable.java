package com.eminus.cell;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.eminus.Eminus;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class StateTable implements StateOpacity {
    public static final String DICTIONARY_NAME = "state";
    public static final int FULL_OPACITY = 15;
    public static final BlockState PLACEHOLDER = Blocks.CONCRETE.pick(DyeColor.MAGENTA).defaultBlockState();

    private static final int INITIAL_CAPACITY = 256;
    private static final int UNKNOWN_OPACITY = -1;

    private final Dictionary<String> ids;
    private final Map<BlockState, Integer> byState = new ConcurrentHashMap<>();
    private final StateOpacity seeThroughLeaves = this::lightDampening;

    private volatile BlockState[] states = new BlockState[INITIAL_CAPACITY];
    private volatile int[] opacities = newOpacities(INITIAL_CAPACITY);
    private volatile int[] dampenings = newOpacities(INITIAL_CAPACITY);

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
        int[] snapshot = opacities;
        if (stateId >= 0 && stateId < snapshot.length && snapshot[stateId] != UNKNOWN_OPACITY) {
            return snapshot[stateId];
        }

        return opacityOf(resolve(stateId));
    }

    public StateOpacity seeThroughLeaves() {
        return seeThroughLeaves;
    }

    public int size() {
        return ids.size();
    }

    private int lightDampening(int stateId) {
        int[] snapshot = dampenings;
        if (stateId >= 0 && stateId < snapshot.length && snapshot[stateId] != UNKNOWN_OPACITY) {
            return snapshot[stateId];
        }

        return resolve(stateId).getLightDampening();
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
        BlockState[] currentStates = states;
        int[] currentOpacities = opacities;
        int[] currentDampenings = dampenings;

        if (id >= currentStates.length) {
            int size = Math.max(currentStates.length * 2, id + 1);
            currentStates = Arrays.copyOf(currentStates, size);
            currentOpacities = grown(currentOpacities, size);
            currentDampenings = grown(currentDampenings, size);
        }

        currentStates[id] = state;
        currentOpacities[id] = opacityOf(state);
        currentDampenings[id] = state.getLightDampening();
        states = currentStates;
        opacities = currentOpacities;
        dampenings = currentDampenings;
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

    private static int[] grown(int[] values, int size) {
        int[] copy = Arrays.copyOf(values, size);
        Arrays.fill(copy, values.length, size, UNKNOWN_OPACITY);
        return copy;
    }

    private static int[] newOpacities(int capacity) {
        int[] created = new int[capacity];
        Arrays.fill(created, UNKNOWN_OPACITY);
        return created;
    }
}
