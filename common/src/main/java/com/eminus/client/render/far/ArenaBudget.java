package com.eminus.client.render.far;

import java.util.Optional;
import java.util.OptionalLong;

import com.eminus.Eminus;
import com.eminus.gpu.Capabilities;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.Sparse;
import com.eminus.render.arena.ArenaSizing;

import org.jspecify.annotations.Nullable;

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

    static @Nullable Sparse sparse(Gpu gpu) {
        Optional<Sparse> sparse = gpu.sparse();
        if (sparse.isEmpty()) {
            return null;
        }

        long pageBytes = sparse.get().pageBytes();
        if (ArenaSizing.blocksPerPage(pageBytes) == ArenaSizing.NO_PAGES) {
            Eminus.LOGGER.info("Sparse pages of {} bytes do not hold whole arena blocks of {} bytes: the arena is a"
                    + " plain buffer", pageBytes, ArenaSizing.BLOCK_BYTES);
            return null;
        }

        return sparse.get();
    }

    static long bytes(long wanted, long ceiling, @Nullable Sparse sparse) {
        return sparse == null
                ? ArenaSizing.fitted(bounded(wanted, ceiling), ceiling)
                : ArenaSizing.reserved(ArenaSizing.fitted(bounded(ceiling, ceiling), ceiling), sparse.pageBytes());
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
