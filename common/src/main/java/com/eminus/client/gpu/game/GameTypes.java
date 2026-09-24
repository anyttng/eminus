package com.eminus.client.gpu.game;

import java.util.Set;

import com.eminus.gpu.Format;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pipeline.Binding;
import com.eminus.gpu.pipeline.Blend;
import com.eminus.gpu.pipeline.DepthCompare;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.TextureUsage;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;

public final class GameTypes {
    private static final BlendFunction MULTIPLY = new BlendFunction(BlendFactor.ZERO, BlendFactor.SRC_COLOR,
            BlendFactor.ZERO, BlendFactor.ONE);

    private GameTypes() {
    }

    public static GpuFormat format(Format format) {
        return switch (format) {
            case RGBA8_UNORM -> GpuFormat.RGBA8_UNORM;
            case R8_UNORM -> GpuFormat.R8_UNORM;
            case D32_FLOAT -> GpuFormat.D32_FLOAT;
            case R32_UINT -> GpuFormat.R32_UINT;
            case RG32_UINT -> GpuFormat.RG32_UINT;
            case RGBA32_UINT -> GpuFormat.RGBA32_UINT;
            case RGBA32_FLOAT -> GpuFormat.RGBA32_FLOAT;
        };
    }

    static Format format(GpuFormat format) {
        for (Format candidate : Format.values()) {
            if (format(candidate) == format) {
                return candidate;
            }
        }
        throw new IllegalStateException("The game's texture format " + format + " has no port format");
    }

    public static GpuBuffer buffer(Buffer buffer) {
        return ((GameBuffer) buffer).buffer();
    }

    public static GpuBufferSlice indexedIndirect(Buffer commands, int firstCommand, int count) {
        return buffer(commands).slice((long) firstCommand * Pass.INDEXED_INDIRECT_BYTES,
                (long) count * Pass.INDEXED_INDIRECT_BYTES);
    }

    public static CompareOp compare(DepthCompare compare) {
        return switch (compare) {
            case GREATER_OR_EQUAL -> CompareOp.GREATER_THAN_OR_EQUAL;
            case LESS_OR_EQUAL -> CompareOp.LESS_THAN_OR_EQUAL;
        };
    }

    static BlendFunction blend(Blend blend) {
        return switch (blend) {
            case TRANSLUCENT -> BlendFunction.TRANSLUCENT;
            case TRANSLUCENT_PREMULTIPLIED -> BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA;
            case MULTIPLY -> MULTIPLY;
        };
    }

    static UniformType uniformType(Binding.Kind kind) {
        return switch (kind) {
            case UNIFORM -> UniformType.UNIFORM_BUFFER;
            case TEXEL -> UniformType.TEXEL_BUFFER;
            case SAMPLED -> UniformType.COMBINED_IMAGE_SAMPLER;
            case STORAGE -> throw new IllegalArgumentException("The game's GPU abstraction has no storage buffers");
        };
    }

    static int bufferUsage(Set<BufferUsage> usage) {
        int flags = 0;
        for (BufferUsage each : usage) {
            flags |= switch (each) {
                case VERTEX -> GpuBuffer.USAGE_VERTEX;
                case UNIFORM -> GpuBuffer.USAGE_UNIFORM;
                case TEXEL -> GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER;
                case INDIRECT -> GpuBuffer.USAGE_INDIRECT_PARAMETERS;
                case COPY_DST -> GpuBuffer.USAGE_COPY_DST;
                case STORAGE -> throw new IllegalArgumentException("The game's GPU abstraction has no storage buffers");
            };
        }
        return flags;
    }

    static int textureUsage(Set<TextureUsage> usage) {
        int flags = 0;
        for (TextureUsage each : usage) {
            flags |= switch (each) {
                case ATTACHMENT -> GpuTexture.USAGE_RENDER_ATTACHMENT;
                case SAMPLED -> GpuTexture.USAGE_TEXTURE_BINDING;
                case COPY_DST -> GpuTexture.USAGE_COPY_DST;
            };
        }
        return flags;
    }

    static GpuSampler sampler(Sampler sampler) {
        return switch (sampler) {
            case NEAREST -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            case LINEAR -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            case NEAREST_MIPPED -> RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, true);
        };
    }
}
