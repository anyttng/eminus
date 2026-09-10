package com.eminus.mesh;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.eminus.cell.DetailLevel;

import org.jspecify.annotations.Nullable;

public final class MeshQueue {
    public static final int CLASSES = DetailLevel.COUNT * 2;

    private static final int PER_LEVEL = 2;
    private static final int RETRY_OFFSET = 1;

    private final List<Deque<MeshTask>> classes = new ArrayList<>(CLASSES);

    private int size;

    public MeshQueue() {
        for (int index = 0; index < CLASSES; index++) {
            classes.add(new ArrayDeque<>());
        }
    }

    public synchronized void add(MeshTask task) {
        classes.get(classOf(task)).addLast(task);
        size++;
    }

    public synchronized @Nullable MeshTask poll() {
        for (Deque<MeshTask> waiting : classes) {
            MeshTask task = waiting.pollFirst();
            if (task != null) {
                size--;
                return task;
            }
        }

        return null;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized void clear() {
        classes.forEach(Deque::clear);
        size = 0;
    }

    static int classOf(MeshTask task) {
        return (DetailLevel.MAX - task.level()) * PER_LEVEL + (task.retried() ? RETRY_OFFSET : 0);
    }
}
