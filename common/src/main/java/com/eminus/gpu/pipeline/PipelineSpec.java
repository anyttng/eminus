package com.eminus.gpu.pipeline;

import java.util.ArrayList;
import java.util.List;

import com.eminus.gpu.Format;

import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

public record PipelineSpec(
        Identifier location,
        Identifier vertexShader,
        Identifier fragmentShader,
        List<Binding> bindings,
        List<Define> defines,
        ColourTarget colour,
        @Nullable DepthTest depth) {

    public static Builder builder(Identifier location, Identifier vertexShader, Identifier fragmentShader) {
        return new Builder(location, vertexShader, fragmentShader);
    }

    public record Define(String name, @Nullable Number value) {
    }

    public record ColourTarget(Format format, @Nullable Blend blend, boolean writes) {
    }

    public record DepthTest(DepthCompare compare, boolean writes) {
    }

    public static final class Builder {
        private final Identifier location;
        private final Identifier vertexShader;
        private final Identifier fragmentShader;
        private final List<Binding> bindings = new ArrayList<>();
        private final List<Define> defines = new ArrayList<>();
        private @Nullable ColourTarget colour;
        private @Nullable DepthTest depth;

        private Builder(Identifier location, Identifier vertexShader, Identifier fragmentShader) {
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

        public Builder withColourTarget(Format format, @Nullable Blend blend, boolean writes) {
            colour = new ColourTarget(format, blend, writes);
            return this;
        }

        public Builder withDepthTest(DepthCompare compare, boolean writes) {
            depth = new DepthTest(compare, writes);
            return this;
        }

        public PipelineSpec build() {
            if (colour == null) {
                throw new IllegalStateException("Pipeline " + location + " declares no colour target");
            }

            return new PipelineSpec(location, vertexShader, fragmentShader, List.copyOf(bindings),
                    List.copyOf(defines), colour, depth);
        }
    }
}
