package com.eminus.cell;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Dictionary<T> {
    @FunctionalInterface
    public interface Persistence<T> {
        void persist(int id, T value);
    }

    public static final int MISSING = -1;

    private static final int INITIAL_CAPACITY = 256;

    private final Persistence<T> persistence;
    private final Map<T, Integer> ids = new ConcurrentHashMap<>();

    private volatile Object[] values = new Object[INITIAL_CAPACITY];
    private volatile int size;

    public Dictionary(Persistence<T> persistence) {
        this.persistence = persistence;
    }

    public synchronized int register(T value) {
        Integer existing = ids.get(value);
        if (existing != null) {
            return existing;
        }

        int id = size;
        persistence.persist(id, value);
        store(id, value);
        return id;
    }

    public synchronized void load(int id, T value) {
        store(id, value);
    }

    public int id(T value) {
        Integer id = ids.get(value);
        return id == null ? MISSING : id;
    }

    @SuppressWarnings("unchecked")
    public T value(int id) {
        Object[] snapshot = values;
        return id >= 0 && id < snapshot.length ? (T) snapshot[id] : null;
    }

    public int size() {
        return size;
    }

    private void store(int id, T value) {
        Object[] current = values;
        if (id >= current.length) {
            current = Arrays.copyOf(current, Math.max(current.length * 2, id + 1));
        }

        current[id] = value;
        values = current;
        ids.put(value, id);
        size = Math.max(size, id + 1);
    }
}
