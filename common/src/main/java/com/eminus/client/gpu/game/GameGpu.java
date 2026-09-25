package com.eminus.client.gpu.game;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.eminus.gpu.Capabilities;
import com.eminus.gpu.Format;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.gpu.buffer.Staging;
import com.eminus.gpu.buffer.TexelView;
import com.eminus.gpu.compute.Compute;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pass.PassSpec;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Texture;
import com.eminus.gpu.texture.TextureUsage;
import com.eminus.mixin.GpuDeviceAccessor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.textures.GpuTexture;

import net.minecraft.client.Minecraft;

public final class GameGpu implements Gpu {
    private static final int LAYERS = 1;
    private static final int BASE_LAYER = 0;

    private final GpuDevice device;
    private final Capabilities capabilities;
    private final List<GamePipeline> pipelines = new ArrayList<>();

    private GameGpu(GpuDevice device, Capabilities capabilities) {
        this.device = device;
        this.capabilities = capabilities;
    }

    public static GameGpu create() {
        RenderSystem.assertOnRenderThread();
        GpuDevice device = RenderSystem.getDevice();
        DeviceInfo info = device.getDeviceInfo();
        DeviceReading reading = DeviceReading.read(device);

        return new GameGpu(device, new Capabilities(info.backendName(), info.isZZeroToOne(),
                info.features().drawIndirect(), info.features().multiDrawIndirect(),
                info.features().persistentMapping(), info.limits().maxMemoryAllocationSize(),
                reading.texelElements(), reading.freeBytes()));
    }

    public static void reserveQuadIndices(int maxIndices) {
        RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(maxIndices);
    }

    public static String backendName() {
        RenderSystem.assertOnRenderThread();
        return RenderSystem.getDevice().getDeviceInfo().backendName();
    }

    @Override
    public Capabilities capabilities() {
        return capabilities;
    }

    @Override
    public int maxTextureSide(Format format) {
        return device.getDeviceInfo().limits().maxTextureSizeForFormat(GameTypes.format(format));
    }

    @Override
    public void assertRenderThread() {
        RenderSystem.assertOnRenderThread();
    }

    @Override
    public Buffer buffer(String label, Set<BufferUsage> usage, long bytes) {
        return new GameBuffer(device.createBuffer(() -> label, GameTypes.bufferUsage(usage), bytes));
    }

    @Override
    public Buffer buffer(String label, Set<BufferUsage> usage, ByteBuffer data) {
        return new GameBuffer(device.createBuffer(() -> label, GameTypes.bufferUsage(usage), data));
    }

    @Override
    public void write(Buffer target, long offset, ByteBuffer data) {
        GpuBuffer buffer = ((GameBuffer) target).buffer();
        device.createCommandEncoder().writeToBuffer(buffer.slice(offset, data.remaining()), data);
    }

    @Override
    public TexelView texelView(Buffer buffer, Format format) {
        return new GameTexelView(buffer, format);
    }

    @Override
    public Texture texture(String label, Set<TextureUsage> usage, Format format, int width, int height, int mips) {
        GpuTexture texture = device.createTexture(label, GameTypes.textureUsage(usage), GameTypes.format(format),
                width, height, LAYERS, mips);
        return GameTexture.owned(texture, device.createTextureView(texture), format);
    }

    @Override
    public void write(Texture target, int mip, int x, int y, int width, int height, ByteBuffer data) {
        device.createCommandEncoder()
                .writeToTexture(((GameTexture) target).texture(), data, mip, BASE_LAYER, x, y, width, height);
    }

    @Override
    public Texture mainColour() {
        return GameTexture.borrowed(Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView());
    }

    @Override
    public Texture mainDepth() {
        return GameTexture.borrowed(Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView());
    }

    @Override
    public Texture lightmap() {
        return GameTexture.borrowed(Minecraft.getInstance().gameRenderer.lightmap());
    }

    @Override
    public Pipeline pipeline(PipelineSpec spec) {
        GamePipeline pipeline = GamePipeline.of(spec);
        pipelines.add(pipeline);
        return pipeline;
    }

    @Override
    public Pass pass(PassSpec spec) {
        return GamePass.open(spec);
    }

    @Override
    public Staging staging(String label, int bytes, boolean persistentlyMapped) {
        return GameStaging.create(label, bytes, persistentlyMapped);
    }

    @Override
    public Optional<Compute> compute() {
        return Optional.empty();
    }

    @Override
    public void close() {
        GpuDeviceBackend backend = ((GpuDeviceAccessor) device).eminus$backend();
        pipelines.forEach(pipeline -> pipeline.release(backend));
        pipelines.clear();
    }
}
