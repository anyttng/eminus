package com.eminus.render.far;

import net.minecraft.util.Mth;

public record CameraOrigin(int blockX, int blockY, int blockZ, float offsetX, float offsetY, float offsetZ) {
    public static CameraOrigin of(double x, double y, double z) {
        int blockX = Mth.floor(x);
        int blockY = Mth.floor(y);
        int blockZ = Mth.floor(z);
        return new CameraOrigin(blockX, blockY, blockZ, (float) (blockX - x), (float) (blockY - y),
                (float) (blockZ - z));
    }
}
