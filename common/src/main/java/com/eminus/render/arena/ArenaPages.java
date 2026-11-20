package com.eminus.render.arena;

import it.unimi.dsi.fastutil.ints.IntArrayList;

public final class ArenaPages {
    private static final int NO_PAGE = -1;

    private final int segmentsPerPage;
    private final int[] used;
    private final boolean[] held;
    private final IntArrayList emptied = new IntArrayList();

    private int committed;

    public ArenaPages(int segments, int segmentsPerPage) {
        if (segments <= 0 || segmentsPerPage <= 0) {
            throw new IllegalArgumentException(
                    "An arena of " + segments + " segments in pages of " + segmentsPerPage
                            + " segments holds nothing.");
        }

        this.segmentsPerPage = segmentsPerPage;
        this.used = new int[Math.ceilDiv(segments, segmentsPerPage)];
        this.held = new boolean[used.length];
    }

    public int segmentsPerPage() {
        return segmentsPerPage;
    }

    public int pages() {
        return used.length;
    }

    public int committed() {
        return committed;
    }

    public void place(int segment, int segments, PageRuns commits) {
        if (segments <= 0) {
            return;
        }

        int end = segment + segments;
        int lastPage = (end - 1) / segmentsPerPage;
        int runStart = NO_PAGE;

        for (int page = segment / segmentsPerPage; page <= lastPage; page++) {
            count(page, overlap(page, segment, end), segment, end);
            if (held[page]) {
                runStart = close(commits, runStart, page);
                continue;
            }

            held[page] = true;
            committed++;
            runStart = runStart == NO_PAGE ? page : runStart;
        }

        close(commits, runStart, lastPage + 1);
    }

    public void free(int segment, int segments) {
        if (segments <= 0) {
            return;
        }

        int end = segment + segments;

        for (int page = segment / segmentsPerPage; page <= (end - 1) / segmentsPerPage; page++) {
            count(page, -overlap(page, segment, end), segment, end);
            if (used[page] == 0) {
                emptied.add(page);
            }
        }
    }

    public void drainEmptied(PageRuns decommits) {
        int runStart = NO_PAGE;
        int runEnd = NO_PAGE;

        for (int index = 0; index < emptied.size(); index++) {
            int page = emptied.getInt(index);
            if (used[page] != 0 || !held[page]) {
                continue;
            }

            held[page] = false;
            committed--;
            if (page != runEnd) {
                close(decommits, runStart, runEnd);
                runStart = page;
            }

            runEnd = page + 1;
        }

        close(decommits, runStart, runEnd);
        emptied.clear();
    }

    private int overlap(int page, int segment, int end) {
        return Math.min(end, (page + 1) * segmentsPerPage) - Math.max(segment, page * segmentsPerPage);
    }

    private void count(int page, int change, int segment, int end) {
        used[page] += change;
        if (used[page] < 0 || used[page] > segmentsPerPage) {
            throw new IllegalStateException("Page " + page + " holds " + used[page] + " of " + segmentsPerPage
                    + " segments after " + (change > 0 ? "placing" : "freeing") + " segments " + segment + ".." + end);
        }
    }

    private static int close(PageRuns runs, int runStart, int runEnd) {
        if (runStart != NO_PAGE) {
            runs.run(runStart, runEnd - runStart);
        }

        return NO_PAGE;
    }

    @FunctionalInterface
    public interface PageRuns {
        void run(int first, int count);
    }
}
