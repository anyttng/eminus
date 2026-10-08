package com.eminus.cell;

@FunctionalInterface
public interface StateOpacity {
    int opacity(int stateId);

    default boolean cover(int stateId) {
        return false;
    }

    default boolean coversGround(long entry, int voxelBlocks) {
        return voxelBlocks - VoxelEntry.lowGap(entry) - VoxelEntry.highGap(entry) > 1
                && cover(VoxelEntry.state(entry));
    }
}
