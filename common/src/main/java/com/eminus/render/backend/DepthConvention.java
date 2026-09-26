package com.eminus.render.backend;

import com.eminus.gpu.pipeline.DepthCompare;
import com.eminus.gpu.pipeline.PipelineSpec;

public record DepthConvention(boolean zeroToOne, boolean reversed) {
    private static final double LOW = 0.0;
    private static final double HIGH = 1.0;

    public static DepthConvention of(boolean zeroToOne, boolean reversed) {
        return new DepthConvention(zeroToOne, reversed);
    }

    public DepthCompare compare() {
        return reversed ? DepthCompare.GREATER_OR_EQUAL : DepthCompare.LESS_OR_EQUAL;
    }

    public DepthCompare fartherWins() {
        return reversed ? DepthCompare.LESS_OR_EQUAL : DepthCompare.GREATER_OR_EQUAL;
    }

    public double farthest() {
        return reversed ? LOW : HIGH;
    }

    public double nearest() {
        return reversed ? HIGH : LOW;
    }

    public PipelineSpec.Builder define(PipelineSpec.Builder builder) {
        builder.withDefine("FARTHEST", (float) farthest()).withDefine("NEAREST", (float) nearest());
        if (zeroToOne) {
            builder.withDefine("DEPTH_ZERO_TO_ONE");
        }
        return reversed ? builder.withDefine("DEPTH_REVERSED") : builder;
    }

    public String range() {
        return zeroToOne ? "0..1" : "-1..1";
    }

    public String direction() {
        return reversed ? "reversed" : "forward";
    }
}
