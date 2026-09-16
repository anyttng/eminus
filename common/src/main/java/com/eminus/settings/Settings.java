package com.eminus.settings;

public record Settings(
        boolean ingestion,
        int lowestStoredLevel,
        int farRenderCells,
        int workerThreads,
        DetailDistance detailDistance,
        boolean fog,
        boolean fade) {

    public static final boolean DEFAULT_INGESTION = true;
    public static final int DEFAULT_LOWEST_STORED_LEVEL = 0;
    public static final int DEFAULT_FAR_RENDER_CELLS = 16;
    public static final DetailDistance DEFAULT_DETAIL_DISTANCE = DetailDistance.MEDIUM;
    public static final boolean DEFAULT_FOG = true;
    public static final boolean DEFAULT_FADE = true;

    public static final int MIN_DETAIL_LEVEL = 0;
    public static final int MAX_DETAIL_LEVEL = 4;
    public static final int MIN_FAR_RENDER_CELLS = 1;
    public static final int MAX_FAR_RENDER_CELLS = 64;
    public static final int MIN_WORKER_THREADS = 1;
    public static final int MAX_WORKER_THREADS = 32;

    private static final double CORES_PER_WORKER = 1.5;
    private static final int MAX_DEFAULT_WORKER_THREADS = 8;

    public static Settings defaults() {
        return new Settings(
                DEFAULT_INGESTION,
                DEFAULT_LOWEST_STORED_LEVEL,
                DEFAULT_FAR_RENDER_CELLS,
                defaultWorkerThreads(Runtime.getRuntime().availableProcessors()),
                DEFAULT_DETAIL_DISTANCE,
                DEFAULT_FOG,
                DEFAULT_FADE);
    }

    public static int defaultWorkerThreads(int cores) {
        return Math.clamp((int) (cores / CORES_PER_WORKER), MIN_WORKER_THREADS, MAX_DEFAULT_WORKER_THREADS);
    }

    public Settings withIngestion(boolean value) {
        return new Settings(value, lowestStoredLevel, farRenderCells, workerThreads, detailDistance, fog, fade);
    }

    public Settings withLowestStoredLevel(int value) {
        return new Settings(ingestion, value, farRenderCells, workerThreads, detailDistance, fog, fade);
    }

    public Settings withFarRenderCells(int value) {
        return new Settings(ingestion, lowestStoredLevel, value, workerThreads, detailDistance, fog, fade);
    }

    public Settings withWorkerThreads(int value) {
        return new Settings(ingestion, lowestStoredLevel, farRenderCells, value, detailDistance, fog, fade);
    }

    public Settings withDetailDistance(DetailDistance value) {
        return new Settings(ingestion, lowestStoredLevel, farRenderCells, workerThreads, value, fog, fade);
    }

    public Settings withFog(boolean value) {
        return new Settings(ingestion, lowestStoredLevel, farRenderCells, workerThreads, detailDistance, value, fade);
    }

    public Settings withFade(boolean value) {
        return new Settings(ingestion, lowestStoredLevel, farRenderCells, workerThreads, detailDistance, fog, value);
    }
}
