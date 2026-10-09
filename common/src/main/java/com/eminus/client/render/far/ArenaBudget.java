package com.eminus.client.render.far;

import java.util.OptionalLong;

import com.eminus.Eminus;
import com.eminus.gpu.Capabilities;
import com.eminus.render.arena.ArenaSizing;

final class ArenaBudget {
    private static final long BYTES_PER_MIB = 1L << 20;

    private ArenaBudget() {
    }

    static long ceiling(Capabilities device, long replacedArenaBytes) {
        OptionalLong texelBytes = device.texelElements().isPresent()
                ? OptionalLong.of(device.texelElements().getAsLong() * ArenaSizing.QUAD_BYTES)
                : OptionalLong.empty();
        OptionalLong freeBytes = device.freeBytes().isPresent()
                ? OptionalLong.of(device.freeBytes().getAsLong() + replacedArenaBytes)
                : OptionalLong.empty();
        long maxAllocation = device.maxAllocationBytes();
        long ceiling = ArenaSizing.ceiling(texelBytes, maxAllocation, freeBytes);

        Eminus.LOGGER.info("Geometry arena ceiling {} MiB: texel buffer {}, vertex index {} MiB, device share {}, "
                + "free video memory {}", ceiling / BYTES_PER_MIB,
                texelBytes.isPresent() ? texelBytes.getAsLong() / BYTES_PER_MIB + " MiB"
                        : "unread, " + ArenaSizing.UNREAD_TEXEL_BYTES / BYTES_PER_MIB + " MiB in its place",
                ArenaSizing.VERTEX_INDEX_BYTES / BYTES_PER_MIB,
                maxAllocation == Long.MAX_VALUE ? "unbounded"
                        : maxAllocation / ArenaSizing.DEVICE_SHARE / BYTES_PER_MIB + " MiB",
                freeBytes.isPresent() ? freeBytes.getAsLong() / BYTES_PER_MIB + " MiB with the replaced arena's "
                        + replacedArenaBytes / BYTES_PER_MIB + " MiB, "
                        + freeBytes.getAsLong() / ArenaSizing.FREE_MEMORY_SHARE / BYTES_PER_MIB + " MiB taken"
                        : "not reported");
        return ceiling;
    }

    static long bounded(long wanted, long ceiling) {
        long bytes = wanted;
        Long floorMiB = Long.getLong(FarRenderer.ARENA_FLOOR_PROPERTY);
        if (floorMiB != null) {
            Eminus.LOGGER.info("Geometry arena raised to at least {} MiB by -D{}, {} MiB wanted", floorMiB,
                    FarRenderer.ARENA_FLOOR_PROPERTY, wanted / BYTES_PER_MIB);
            bytes = Math.max(bytes, floorMiB * BYTES_PER_MIB);
            if (bytes > ceiling) {
                Eminus.LOGGER.info("Geometry arena floor of {} MiB is above the device ceiling: the arena starts at "
                        + "{} MiB", floorMiB, ceiling / BYTES_PER_MIB);
            }
        }

        Long capMiB = Long.getLong(FarRenderer.ARENA_CAP_PROPERTY);
        if (capMiB != null) {
            Eminus.LOGGER.info("Geometry arena capped at {} MiB by -D{}, {} MiB wanted", capMiB,
                    FarRenderer.ARENA_CAP_PROPERTY, wanted / BYTES_PER_MIB);
            bytes = Math.min(bytes, capMiB * BYTES_PER_MIB);
        }

        return bytes;
    }
}
