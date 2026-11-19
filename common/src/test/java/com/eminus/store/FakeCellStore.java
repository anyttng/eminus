package com.eminus.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.eminus.cell.Cell;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.Dictionary;

public final class FakeCellStore implements CellStore {
    private final Map<Long, byte[]> records = new ConcurrentHashMap<>();
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger writes = new AtomicInteger();
    private final AtomicInteger writeAttempts = new AtomicInteger();

    private volatile CountDownLatch readGate;
    private volatile CountDownLatch readStarted;
    private volatile CountDownLatch writeGate;
    private volatile CountDownLatch writeStarted;
    private volatile boolean refuseWrites;
    private volatile Throwable readFailure;

    @Override
    public Cell read(long key) {
        reads.incrementAndGet();

        CountDownLatch gate = readGate;
        if (gate != null) {
            readStarted.countDown();
            awaitQuietly(gate);
        }

        Throwable failure = readFailure;
        if (failure instanceof RuntimeException refused) {
            throw refused;
        }

        if (failure instanceof Error broken) {
            throw broken;
        }

        byte[] record = records.get(key);
        return record == null ? null : CellRecord.decode(key, record);
    }

    @Override
    public void write(long key, int occupancy, byte[] record) {
        writeAttempts.incrementAndGet();
        if (refuseWrites) {
            throw new StoreException("The fake store refuses writes.", null);
        }

        CountDownLatch gate = writeGate;
        if (gate != null) {
            writeStarted.countDown();
            awaitQuietly(gate);
        }

        writes.incrementAndGet();
        records.put(key, record);
    }

    @Override
    public void delete(long key) {
        records.remove(key);
    }

    @Override
    public void putDictionaryEntry(String name, int id, String value) {
    }

    @Override
    public void readDictionary(String name, Dictionary.Persistence<String> into) {
    }

    @Override
    public void putColumn(long chunk, int lowestLevel) {
    }

    @Override
    public void readColumns(ColumnCoverage.Loader into) {
    }

    @Override
    public void putBeam(long pos, byte[] sections) {
    }

    @Override
    public void deleteBeam(long pos) {
    }

    @Override
    public void readBeams(Rows into) {
    }

    @Override
    public void maintain() {
    }

    @Override
    public void leaveUnclean() {
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

    public int writeAttempts() {
        return writeAttempts.get();
    }

    public void refuseWrites(boolean refuse) {
        refuseWrites = refuse;
    }

    public void failReads(Throwable failure) {
        readFailure = failure;
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

    public void holdWrites() {
        writeStarted = new CountDownLatch(1);
        writeGate = new CountDownLatch(1);
    }

    public boolean awaitWriteStarted(long millis) {
        return await(writeStarted, millis);
    }

    public void releaseWrites() {
        CountDownLatch gate = writeGate;
        writeGate = null;
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
