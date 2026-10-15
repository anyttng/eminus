package com.eminus.settings;

public record Settings(
        boolean ingestion,
        LodQuality lodQuality,
        int farRenderCells,
        int workerThreads,
        DetailDistance detailDistance,
        boolean fog,
        boolean fade,
        ShaderPackLod shaderPackLod,
        boolean lodAnimations) {

    public static final boolean DEFAULT_INGESTION = true;
    public static final LodQuality DEFAULT_LOD_QUALITY = LodQuality.FULL;
    public static final int DEFAULT_FAR_RENDER_CELLS = 16;
    public static final DetailDistance DEFAULT_DETAIL_DISTANCE = DetailDistance.MEDIUM;
    public static final boolean DEFAULT_FOG = true;
    public static final boolean DEFAULT_FADE = true;
    public static final ShaderPackLod DEFAULT_SHADER_PACK_LOD = ShaderPackLod.DEFAULT;
    public static final boolean DEFAULT_LOD_ANIMATIONS = true;

    public static final int MIN_FAR_RENDER_CELLS = 1;
    public static final int MAX_FAR_RENDER_CELLS = 64;
    public static final int MIN_WORKER_THREADS = 1;
    public static final int MAX_WORKER_THREADS = 32;

    private static final double CORES_PER_WORKER = 1.5;
    private static final int MAX_DEFAULT_WORKER_THREADS = 8;

    public static Settings defaults() {
        return new Settings(
                DEFAULT_INGESTION,
                DEFAULT_LOD_QUALITY,
                DEFAULT_FAR_RENDER_CELLS,
                defaultWorkerThreads(Runtime.getRuntime().availableProcessors()),
                DEFAULT_DETAIL_DISTANCE,
                DEFAULT_FOG,
                DEFAULT_FADE,
                DEFAULT_SHADER_PACK_LOD,
                DEFAULT_LOD_ANIMATIONS);
    }

    public static int defaultWorkerThreads(int cores) {
        return Math.clamp((int) (cores / CORES_PER_WORKER), MIN_WORKER_THREADS, MAX_DEFAULT_WORKER_THREADS);
    }

    public Settings withIngestion(boolean value) {
        return new Settings(value, lodQuality, farRenderCells, workerThreads, detailDistance, fog, fade,
                shaderPackLod, lodAnimations);
    }

    public Settings withLodQuality(LodQuality value) {
        return new Settings(ingestion, value, farRenderCells, workerThreads, detailDistance, fog, fade,
                shaderPackLod, lodAnimations);
    }

    public Settings withFarRenderCells(int value) {
        return new Settings(ingestion, lodQuality, value, workerThreads, detailDistance, fog, fade,
                shaderPackLod, lodAnimations);
    }

    public Settings withWorkerThreads(int value) {
        return new Settings(ingestion, lodQuality, farRenderCells, value, detailDistance, fog, fade,
                shaderPackLod, lodAnimations);
    }

    public Settings withDetailDistance(DetailDistance value) {
        return new Settings(ingestion, lodQuality, farRenderCells, workerThreads, value, fog, fade,
                shaderPackLod, lodAnimations);
    }

    public Settings withFog(boolean value) {
        return new Settings(ingestion, lodQuality, farRenderCells, workerThreads, detailDistance, value, fade,
                shaderPackLod, lodAnimations);
    }

    public Settings withFade(boolean value) {
        return new Settings(ingestion, lodQuality, farRenderCells, workerThreads, detailDistance, fog, value,
                shaderPackLod, lodAnimations);
    }

    public Settings withShaderPackLod(ShaderPackLod value) {
        return new Settings(ingestion, lodQuality, farRenderCells, workerThreads, detailDistance, fog, fade,
                value, lodAnimations);
    }

    public Settings withLodAnimations(boolean value) {
        return new Settings(ingestion, lodQuality, farRenderCells, workerThreads, detailDistance, fog, fade,
                shaderPackLod, value);
    }
}
