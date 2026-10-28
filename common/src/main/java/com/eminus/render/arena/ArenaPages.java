package com.eminus.render.arena;

public final class ArenaPages {
    private static final int NO_PAGE = -1;

    private final int segmentsPerPage;
    private final int[] used;

    private int committed;

    public ArenaPages(int segments, int segmentsPerPage) {
        if (segments <= 0 || segmentsPerPage <= 0) {
            throw new IllegalArgumentException(
                    "An arena of " + segments + " segments in pages of " + segmentsPerPage
                            + " segments holds nothing.");
        }

        this.segmentsPerPage = segmentsPerPage;
        this.used = new int[Math.ceilDiv(segments, segmentsPerPage)];
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

    public PageRun place(int segment, int segments) {
        return change(segment, segments, true);
    }

    public PageRun free(int segment, int segments) {
        return change(segment, segments, false);
    }

    private PageRun change(int segment, int segments, boolean placed) {
        if (segments <= 0) {
            return PageRun.NONE;
        }

        int end = segment + segments;
        int first = NO_PAGE;
        int last = NO_PAGE;
        int changed = 0;

        for (int page = segment / segmentsPerPage; page <= (end - 1) / segmentsPerPage; page++) {
            int overlap = Math.min(end, (page + 1) * segmentsPerPage) - Math.max(segment, page * segmentsPerPage);
            boolean emptyBefore = used[page] == 0;
            used[page] += placed ? overlap : -overlap;
            if (used[page] < 0 || used[page] > segmentsPerPage) {
                throw new IllegalStateException("Page " + page + " holds " + used[page] + " of " + segmentsPerPage
                        + " segments after " + (placed ? "placing" : "freeing") + " segments " + segment + ".." + end);
            }

            if (placed ? emptyBefore : used[page] == 0) {
                first = first == NO_PAGE ? page : first;
                last = page;
                changed++;
            }
        }

        if (changed == 0) {
            return PageRun.NONE;
        }

        if (changed != last - first + 1) {
            throw new IllegalStateException("Segments " + segment + ".." + end + " changed pages " + first + ".." + last
                    + " with a gap: the allocator handed out a range that was not free.");
        }

        committed += placed ? changed : -changed;
        return new PageRun(first, changed);
    }

    public record PageRun(int first, int count) {
        public static final PageRun NONE = new PageRun(0, 0);

        public boolean isEmpty() {
            return count == 0;
        }
    }
}
