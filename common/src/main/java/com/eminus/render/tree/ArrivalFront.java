package com.eminus.render.tree;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import org.jspecify.annotations.Nullable;

final class ArrivalFront {
    private static final double NO_FRONT = Double.MAX_VALUE;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private LongOpenHashSet shown = new LongOpenHashSet();
    private LongOpenHashSet shownAncestors = new LongOpenHashSet();
    private LongOpenHashSet nextShown = new LongOpenHashSet();
    private LongOpenHashSet nextAncestors = new LongOpenHashSet();
    private double front = NO_FRONT;
    private double outsideFront = NO_FRONT;
    private double eyeX;
    private double eyeZ;
    private double nearLimit;
    private int waitingNear;
    private long holderKey;
    private @Nullable TreeNode holder;

    void begin(double x, double z, double near) {
        eyeX = x;
        eyeZ = z;
        nearLimit = near;
        front = NO_FRONT;
        outsideFront = NO_FRONT;
        waitingNear = 0;
        holder = null;
    }

    int waitingNear() {
        return waitingNear;
    }

    void waiting(long key, @Nullable TreeNode node) {
        double distance = horizontalDistance(key);
        if (distance < front) {
            front = distance;
            holderKey = key;
            holder = node;
        }

        if (distance < nearLimit) {
            waitingNear++;
        }
    }

    void waitingOutside(long key) {
        outsideFront = Math.min(outsideFront, horizontalDistance(key));
    }

    FrontState state(CellFrame frame, int hiddenBehind, int hiddenUnder, long now) {
        if (front == NO_FRONT) {
            return FrontState.none(hiddenBehind, hiddenUnder);
        }

        String place = holder == null ? FrontState.UNREQUESTED
                : holder.building() ? FrontState.BUILDING : FrontState.FAILED;
        long buildMillis = holder != null && holder.building() ? (now - holder.dispatchedAt()) / NANOS_PER_MILLI : 0L;
        return new FrontState((int) front, CellKey.level(holderKey), CellFrame.originXOf(holderKey),
                frame.originYOf(holderKey), CellFrame.originZOf(holderKey), place, buildMillis, hiddenBehind,
                hiddenUnder);
    }

    boolean holds(long key, boolean inView) {
        double nearest = inView ? front : outsideFront;
        return nearest != NO_FRONT && horizontalDistance(key) > nearest && !shownNear(key);
    }

    void shown(long key) {
        nextShown.add(key);
        long ancestor = key;
        for (int level = CellKey.level(key); level < DetailLevel.MAX; level++) {
            ancestor = CellKey.parent(ancestor);
            if (!nextAncestors.add(ancestor)) {
                return;
            }
        }
    }

    void finish() {
        LongOpenHashSet keys = shown;
        LongOpenHashSet ancestors = shownAncestors;
        shown = nextShown;
        shownAncestors = nextAncestors;
        nextShown = keys;
        nextAncestors = ancestors;
        nextShown.clear();
        nextAncestors.clear();
    }

    private boolean shownNear(long key) {
        if (shown.contains(key) || shownAncestors.contains(key)) {
            return true;
        }

        long ancestor = key;
        for (int level = CellKey.level(key); level < DetailLevel.MAX; level++) {
            ancestor = CellKey.parent(ancestor);
            if (shown.contains(ancestor)) {
                return true;
            }
        }

        return false;
    }

    private double horizontalDistance(long key) {
        int side = DetailLevel.blocksPerCell(CellKey.level(key));
        double dx = axis(CellFrame.originXOf(key), side, eyeX);
        double dz = axis(CellFrame.originZOf(key), side, eyeZ);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double axis(int origin, int side, double eye) {
        return Math.max(origin - eye, Math.max(0.0, eye - (origin + side)));
    }
}
