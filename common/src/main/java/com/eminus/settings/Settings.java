package com.eminus.settings;

public record Settings(
        boolean ingestion,
        int lowestStoredLevel,
        int farRenderCells,
        int workerThreads,
        int subdivisionSize,
        boolean fog) {

    public static final boolean DEFAULT_INGESTION = true;
    public static final int DEFAULT_LOWEST_STORED_LEVEL = 0;
    public static final int DEFAULT_FAR_RENDER_CELLS = 16;
    public static final int DEFAULT_SUBDIVISION_SIZE = 64;
    public static final boolean DEFAULT_FOG = true;

    public static final int MIN_DETAIL_LEVEL = 0;
    public static final int MAX_DETAIL_LEVEL = 4;
    public static final int MIN_FAR_RENDER_CELLS = 1;
    public static final int MAX_FAR_RENDER_CELLS = 64;
    public static final int MIN_WORKER_THREADS = 1;
    public static final int MAX_WORKER_THREADS = 32;
    public static final int MIN_SUBDIVISION_SIZE = 8;
    public static final int MAX_SUBDIVISION_SIZE = 256;

    private static final double CORES_PER_WORKER = 1.5;
    private static final int MAX_DEFAULT_WORKER_THREADS = 8;

    public static Settings defaults() {
        return new Settings(
                DEFAULT_INGESTION,
                DEFAULT_LOWEST_STORED_LEVEL,
                DEFAULT_FAR_RENDER_CELLS,
                defaultWorkerThreads(Runtime.getRuntime().availableProcessors()),
                DEFAULT_SUBDIVISION_SIZE,
                DEFAULT_FOG);
    }

    public static int defaultWorkerThreads(int cores) {
        return Math.clamp((int) (cores / CORES_PER_WORKER), MIN_WORKER_THREADS, MAX_DEFAULT_WORKER_THREADS);
    }

    public Settings withIngestion(boolean value) {
        return new Settings(value, lowestStoredLevel, farRenderCells, workerThreads, subdivisionSize, fog);
    }

    public Settings withLowestStoredLevel(int value) {
        return new Settings(ingestion, value, farRenderCells, workerThreads, subdivisionSize, fog);
    }

    public Settings withFarRenderCells(int value) {
        return new Settings(ingestion, lowestStoredLevel, value, workerThreads, subdivisionSize, fog);
    }

    public Settings withWorkerThreads(int value) {
        return new Settings(ingestion, lowestStoredLevel, farRenderCells, value, subdivisionSize, fog);
    }

    public Settings withSubdivisionSize(int value) {
        return new Settings(ingestion, lowestStoredLevel, farRenderCells, workerThreads, value, fog);
    }

    public Settings withFog(boolean value) {
        return new Settings(ingestion, lowestStoredLevel, farRenderCells, workerThreads, subdivisionSize, value);
    }
}
