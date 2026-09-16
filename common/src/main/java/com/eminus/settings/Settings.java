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
    public static final int MAX_FAR_RENDER_CELLS = 64;
    public static final int MIN_WORKER_THREADS = 1;
    public static final int MAX_WORKER_THREADS = 32;
    public static final int MIN_SUBDIVISION_SIZE = 8;
    public static final int MAX_SUBDIVISION_SIZE = 256;

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
        return Math.clamp((int) (cores / CORES_PER_WORKER), MIN_WORKER_THREADS, MAX_WORKER_THREADS);
    }

    public Settings withIngestion(boolean value) {
        return new Settings(value, lowestStoredLevel, farRenderCells, workerThreads, subdivisionSize, fogMode);
    }

    public Settings withLowestStoredLevel(int value) {
        return new Settings(ingestion, value, farRenderCells, workerThreads, subdivisionSize, fogMode);
    }

    public Settings withFarRenderCells(int value) {
        return new Settings(ingestion, lowestStoredLevel, value, workerThreads, subdivisionSize, fogMode);
    }

    public Settings withWorkerThreads(int value) {
        return new Settings(ingestion, lowestStoredLevel, farRenderCells, value, subdivisionSize, fogMode);
    }

    public Settings withSubdivisionSize(int value) {
        return new Settings(ingestion, lowestStoredLevel, farRenderCells, workerThreads, value, fogMode);
    }

    public Settings withFogMode(FogMode value) {
        return new Settings(ingestion, lowestStoredLevel, farRenderCells, workerThreads, subdivisionSize, value);
    }
}
