package com.eminus.gpu.pipeline;

import java.util.ArrayList;
import java.util.List;

import com.eminus.gpu.Format;
import com.eminus.gpu.Location;

import org.jspecify.annotations.Nullable;

public record PipelineSpec(
        Location location,
        Location vertexShader,
        Location fragmentShader,
        List<Binding> bindings,
        List<Define> defines,
        ColorTarget color,
        @Nullable DepthTest depth) {

    public static Builder builder(Location location, Location vertexShader, Location fragmentShader) {
        return new Builder(location, vertexShader, fragmentShader);
    }

    public record Define(String name, @Nullable Number value) {
    }

    public record ColorTarget(Format format, @Nullable Blend blend, boolean writes) {
    }

    public record DepthTest(DepthCompare compare, boolean writes) {
    }

    public static final class Builder {
        private final Location location;
        private final Location vertexShader;
        private final Location fragmentShader;
        private final List<Binding> bindings = new ArrayList<>();
        private final List<Define> defines = new ArrayList<>();
        private @Nullable ColorTarget color;
        private @Nullable DepthTest depth;

        private Builder(Location location, Location vertexShader, Location fragmentShader) {
            this.location = location;
            this.vertexShader = vertexShader;
            this.fragmentShader = fragmentShader;
        }

        public Builder withBinding(Binding binding) {
            bindings.add(binding);
            return this;
        }

        public Builder withDefine(String name) {
            defines.add(new Define(name, null));
            return this;
        }

        public Builder withDefine(String name, int value) {
            defines.add(new Define(name, value));
            return this;
        }

        public Builder withDefine(String name, float value) {
            defines.add(new Define(name, value));
            return this;
        }

        public Builder withColorTarget(Format format, @Nullable Blend blend, boolean writes) {
            color = new ColorTarget(format, blend, writes);
            return this;
        }

        public Builder withDepthTest(DepthCompare compare, boolean writes) {
            depth = new DepthTest(compare, writes);
            return this;
        }

        public PipelineSpec build() {
            if (color == null) {
                throw new IllegalStateException("Pipeline " + location + " declares no color target");
            }

            return new PipelineSpec(location, vertexShader, fragmentShader, List.copyOf(bindings),
                    List.copyOf(defines), color, depth);
        }
    }
}
