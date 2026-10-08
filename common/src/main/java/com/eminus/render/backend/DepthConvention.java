package com.eminus.render.backend;

import com.eminus.gpu.pipeline.DepthCompare;
import com.eminus.gpu.pipeline.PipelineSpec;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public record DepthConvention(boolean zeroToOne, boolean reversed) {
    private static final double LOW = 0.0;
    private static final double HIGH = 1.0;
    private static final float CLIP_SPAN = 2.0F;

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

    public Matrix4f forward(Matrix4fc projection, Matrix4f target) {
        target.set(projection);
        if (reversed && zeroToOne) {
            return target.m02(target.m03() - CLIP_SPAN * target.m02()).m12(target.m13() - CLIP_SPAN * target.m12())
                    .m22(target.m23() - CLIP_SPAN * target.m22()).m32(target.m33() - CLIP_SPAN * target.m32());
        }
        if (reversed) {
            return target.m02(-target.m02()).m12(-target.m12()).m22(-target.m22()).m32(-target.m32());
        }
        if (zeroToOne) {
            return target.m02(CLIP_SPAN * target.m02() - target.m03()).m12(CLIP_SPAN * target.m12() - target.m13())
                    .m22(CLIP_SPAN * target.m22() - target.m23()).m32(CLIP_SPAN * target.m32() - target.m33());
        }
        return target;
    }

    public String range() {
        return zeroToOne ? "0..1" : "-1..1";
    }

    public String direction() {
        return reversed ? "reversed" : "forward";
    }
}
