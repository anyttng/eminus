package com.eminus.settings;

public record Settings(
        boolean ingestion,
        int lowestStoredLevel,
        int farRenderCells,
        int workerThreads,
        int subdivisionSize,
        FogMode fogMode) {

    public static final boolean DEFAULT_INGESTION = true;
    public static final int DEFAULT_LOWEST_STORED_LEVEL = 0;
    public static final int DEFAULT_FAR_RENDER_CELLS = 16;
    public static final int DEFAULT_SUBDIVISION_SIZE = 64;
    public static final FogMode DEFAULT_FOG_MODE = FogMode.FOG_AND_FADE;

    public static final int MIN_DETAIL_LEVEL = 0;
    public static final int MAX_DETAIL_LEVEL = 4;
    public static final int MIN_FAR_RENDER_CELLS = 1;
    public static final int MIN_WORKER_THREADS = 1;
    public static final int MIN_SUBDIVISION_SIZE = 1;

    private static final double CORES_PER_WORKER = 1.5;

    public static Settings defaults() {
        return new Settings(
                DEFAULT_INGESTION,
                DEFAULT_LOWEST_STORED_LEVEL,
                DEFAULT_FAR_RENDER_CELLS,
                defaultWorkerThreads(Runtime.getRuntime().availableProcessors()),
                DEFAULT_SUBDIVISION_SIZE,
                DEFAULT_FOG_MODE);
    }

    public static int defaultWorkerThreads(int cores) {
        return Math.max(MIN_WORKER_THREADS, (int) (cores / CORES_PER_WORKER));
    }
}
