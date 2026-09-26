package com.eminus.gpu;

import java.util.OptionalLong;

public record Capabilities(
        String backend,
        boolean depthZeroToOne,
        boolean depthReversed,
        boolean lightmapHalfTexel,
        boolean drawIndirect,
        boolean multiDrawIndirect,
        boolean persistentMapping,
        long maxAllocationBytes,
        OptionalLong texelElements,
        OptionalLong freeBytes) {
}
