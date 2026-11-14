package com.eminus.ingest;

import java.util.concurrent.atomic.AtomicInteger;

final class WholeChunkIngest {
    private final AtomicInteger remaining;
    private volatile boolean failed;

    WholeChunkIngest(int sections) {
        remaining = new AtomicInteger(sections);
    }

    boolean sectionDone(boolean merged) {
        if (!merged) {
            failed = true;
        }

        return remaining.decrementAndGet() == 0 && !failed;
    }
}
