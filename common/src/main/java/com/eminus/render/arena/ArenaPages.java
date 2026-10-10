package com.eminus.render.arena;

public final class ArenaPages {
    private static final int NO_PAGE = -1;

    private final int blocksPerPage;
    private final int[] used;

    private int committed;

    public ArenaPages(int blocks, int blocksPerPage) {
        if (blocks <= 0 || blocksPerPage <= 0) {
            throw new IllegalArgumentException(
                    "An arena of " + blocks + " blocks in pages of " + blocksPerPage + " blocks holds nothing.");
        }

        this.blocksPerPage = blocksPerPage;
        this.used = new int[Math.ceilDiv(blocks, blocksPerPage)];
    }

    public int blocksPerPage() {
        return blocksPerPage;
    }

    public int pages() {
        return used.length;
    }

    public int committed() {
        return committed;
    }

    public PageRun place(int block, int blocks) {
        return change(block, blocks, true);
    }

    public PageRun free(int block, int blocks) {
        return change(block, blocks, false);
    }

    private PageRun change(int block, int blocks, boolean placed) {
        if (blocks <= 0) {
            return PageRun.NONE;
        }

        int end = block + blocks;
        int first = NO_PAGE;
        int last = NO_PAGE;
        int changed = 0;

        for (int page = block / blocksPerPage; page <= (end - 1) / blocksPerPage; page++) {
            int overlap = Math.min(end, (page + 1) * blocksPerPage) - Math.max(block, page * blocksPerPage);
            boolean emptyBefore = used[page] == 0;
            used[page] += placed ? overlap : -overlap;
            if (used[page] < 0 || used[page] > blocksPerPage) {
                throw new IllegalStateException("Page " + page + " holds " + used[page] + " of " + blocksPerPage
                        + " blocks after " + (placed ? "placing" : "freeing") + " blocks " + block + ".." + end);
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
            throw new IllegalStateException("Blocks " + block + ".." + end + " changed pages " + first + ".." + last
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
