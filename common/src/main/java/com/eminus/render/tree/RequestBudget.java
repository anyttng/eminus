package com.eminus.render.tree;

public final class RequestBudget {
    public static final int MAX_PER_WALK = 32;

    public static int perWalk(int backlog) {
        return Math.max(0, MAX_PER_WALK - backlog);
    }

    private RequestBudget() {
    }
}
