package com.eminus.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

import com.eminus.cell.Cell;
import com.eminus.cell.Dictionary;

public final class FakeCellStore implements CellStore {
    private final Map<Long, Cell> cells = new ConcurrentHashMap<>();
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger writes = new AtomicInteger();

    private volatile CountDownLatch readGate;
    private volatile CountDownLatch readStarted;
    private volatile boolean refuseWrites;

    @Override
    public Cell read(long key) {
        reads.incrementAndGet();

        CountDownLatch gate = readGate;
        if (gate != null) {
            readStarted.countDown();
            awaitQuietly(gate);
        }

        return cells.get(key);
    }

    @Override
    public void write(Cell cell) {
        if (refuseWrites) {
            throw new StoreException("The fake store refuses writes.", null);
        }

        writes.incrementAndGet();
        cells.put(cell.key(), cell);
    }

    @Override
    public void delete(long key) {
        cells.remove(key);
    }

    @Override
    public void putDictionaryEntry(String name, int id, String value) {
    }

    @Override
    public void readDictionary(String name, Dictionary.Persistence<String> into) {
    }

    @Override
    public void putColumn(long chunk) {
    }

    @Override
    public void readColumns(LongConsumer into) {
    }

    @Override
    public void close() {
    }

    public int reads() {
        return reads.get();
    }

    public int writes() {
        return writes.get();
    }

    public void refuseWrites(boolean refuse) {
        refuseWrites = refuse;
    }

    public void holdReads() {
        readStarted = new CountDownLatch(1);
        readGate = new CountDownLatch(1);
    }

    public boolean awaitReadStarted(long millis) {
        return await(readStarted, millis);
    }

    public void releaseReads() {
        CountDownLatch gate = readGate;
        readGate = null;
        gate.countDown();
    }

    private static void awaitQuietly(CountDownLatch gate) {
        try {
            gate.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean await(CountDownLatch latch, long millis) {
        try {
            return latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
