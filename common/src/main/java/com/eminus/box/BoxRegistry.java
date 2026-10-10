package com.eminus.box;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.eminus.api.v1.FarBox;
import com.eminus.api.v1.FarBoxGroup;

public final class BoxRegistry {
    private static final BoxRegistry CLIENT = new BoxRegistry();

    private final List<Group> groups = new ArrayList<>();
    private long version;

    public static BoxRegistry get() {
        return CLIENT;
    }

    public FarBoxGroup register(String dimension, List<FarBox> boxes) {
        Group group = new Group(Objects.requireNonNull(dimension, "dimension"), List.copyOf(boxes));
        synchronized (this) {
            groups.add(group);
            version++;
        }
        return group;
    }

    public synchronized long version() {
        return version;
    }

    public synchronized BoxSnapshot snapshot(String dimension) {
        List<FarBox> drawn = new ArrayList<>();
        for (Group group : groups) {
            if (group.dimension.equals(dimension)) {
                drawn.addAll(group.boxes);
            }
        }
        return new BoxSnapshot(version, List.copyOf(drawn));
    }

    public synchronized void clear() {
        if (groups.isEmpty()) {
            return;
        }

        for (Group group : groups) {
            group.live = false;
        }
        groups.clear();
        version++;
    }

    private final class Group implements FarBoxGroup {
        private final String dimension;
        private List<FarBox> boxes;
        private boolean live = true;

        private Group(String dimension, List<FarBox> boxes) {
            this.dimension = dimension;
            this.boxes = boxes;
        }

        @Override
        public String dimension() {
            return dimension;
        }

        @Override
        public void update(List<FarBox> replaced) {
            List<FarBox> copied = List.copyOf(replaced);
            synchronized (BoxRegistry.this) {
                if (live) {
                    boxes = copied;
                    version++;
                }
            }
        }

        @Override
        public void remove() {
            synchronized (BoxRegistry.this) {
                if (live) {
                    live = false;
                    groups.remove(this);
                    version++;
                }
            }
        }
    }
}
