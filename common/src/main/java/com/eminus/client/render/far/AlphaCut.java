package com.eminus.client.render.far;

import com.eminus.gpu.pipeline.PipelineSpec;

public record AlphaCut(Compare compare, float reference) {
    private static final String TEST = "ALPHA_TEST";
    private static final String COMPARE_PREFIX = "ALPHA_";

    public enum Compare {
        NEVER(1),
        LESS(2),
        EQUAL(3),
        LEQUAL(4),
        GREATER(5),
        NOTEQUAL(6),
        GEQUAL(7);

        private final int code;

        Compare(int code) {
            this.code = code;
        }
    }

    public static AlphaCut atLeast(float reference) {
        return new AlphaCut(Compare.GEQUAL, reference);
    }

    PipelineSpec.Builder define(PipelineSpec.Builder builder) {
        for (Compare each : Compare.values()) {
            builder.withDefine(COMPARE_PREFIX + each.name(), each.code);
        }
        return builder.withDefine(TEST, compare.code);
    }
}
