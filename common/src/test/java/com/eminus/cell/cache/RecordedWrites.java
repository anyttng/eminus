package com.eminus.cell.cache;

import java.util.concurrent.atomic.AtomicInteger;

import com.eminus.store.StoreException;

public final class RecordedWrites implements WriteLatch.Report {
    private final AtomicInteger firstFailures = new AtomicInteger();
    private final AtomicInteger latches = new AtomicInteger();
    private final AtomicInteger dropAnnouncements = new AtomicInteger();
    private final AtomicInteger recoveries = new AtomicInteger();
    private final AtomicInteger failingCloses = new AtomicInteger();
    private final AtomicInteger droppedCells = new AtomicInteger();

    @Override
    public void firstFailure(long key, StoreException failure) {
        firstFailures.incrementAndGet();
    }

    @Override
    public void latched(int failures) {
        latches.incrementAndGet();
    }

    @Override
    public void dropping(int cap) {
        dropAnnouncements.incrementAndGet();
    }

    @Override
    public void recovered(int dropped) {
        recoveries.incrementAndGet();
        droppedCells.set(dropped);
    }

    @Override
    public void stillFailing(int dropped) {
        failingCloses.incrementAndGet();
        droppedCells.set(dropped);
    }

    public int firstFailures() {
        return firstFailures.get();
    }

    public int latches() {
        return latches.get();
    }

    public int dropAnnouncements() {
        return dropAnnouncements.get();
    }

    public int recoveries() {
        return recoveries.get();
    }

    public int failingCloses() {
        return failingCloses.get();
    }

    public int droppedCells() {
        return droppedCells.get();
    }
}
