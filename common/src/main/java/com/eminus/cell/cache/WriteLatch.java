package com.eminus.cell.cache;

import java.util.function.LongSupplier;

import com.eminus.store.StoreException;

public final class WriteLatch {
    public static final int FAILURE_THRESHOLD = 8;
    public static final long PROBE_MILLIS = 60_000L;
    public static final int MAX_HELD_DIRTY = 1024;

    public interface Report {
        void firstFailure(long key, StoreException failure);

        void latched(int failures);

        void dropping(int cap);

        void recovered(int dropped);

        void stillFailing(int dropped);
    }

    private final LongSupplier clock;
    private final Report report;

    private volatile boolean latched;
    private int failures;
    private long probeAt;
    private int dropped;
    private boolean everDropped;
    private boolean recovered;

    WriteLatch(LongSupplier clock, Report report) {
        this.clock = clock;
        this.report = report;
    }

    boolean latched() {
        return latched;
    }

    synchronized boolean claimWrite() {
        if (!latched) {
            return true;
        }

        long now = clock.getAsLong();
        if (now < probeAt) {
            return false;
        }

        probeAt = now + PROBE_MILLIS;
        return true;
    }

    synchronized void succeeded() {
        failures = 0;
        if (!latched) {
            return;
        }

        latched = false;
        recovered = true;
        report.recovered(dropped);
        dropped = 0;
    }

    synchronized void failed(long key, StoreException failure) {
        if (latched) {
            return;
        }

        failures++;
        if (failures == 1) {
            report.firstFailure(key, failure);
        }

        if (failures >= FAILURE_THRESHOLD) {
            latched = true;
            probeAt = clock.getAsLong() + PROBE_MILLIS;
            report.latched(failures);
        }
    }

    synchronized boolean probeDue() {
        return latched && clock.getAsLong() >= probeAt;
    }

    synchronized void probeNow() {
        probeAt = clock.getAsLong();
    }

    synchronized boolean takeRecovered() {
        boolean taken = recovered;
        recovered = false;
        return taken;
    }

    synchronized void dropped(int count) {
        if (dropped == 0) {
            report.dropping(MAX_HELD_DIRTY);
        }

        dropped += count;
        everDropped = true;
    }

    synchronized boolean flushed() {
        if (latched) {
            report.stillFailing(dropped);
        }

        return !everDropped;
    }
}
