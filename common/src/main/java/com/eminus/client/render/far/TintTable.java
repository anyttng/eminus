package com.eminus.client.render.far;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import com.eminus.cell.Dictionary;
import com.eminus.mesh.Quad;
import com.eminus.model.BiomeColours;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

public final class TintTable implements AutoCloseable {
    public static final int BIOME_STRIDE = Quad.MAX_BIOME_ID + 1;

    private static final String LABEL = "eminus-tint-table";
    private static final int USAGE = GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST;
    private static final int TEXEL_BYTES = Integer.BYTES;

    private final GpuBuffer buffer;
    private final boolean[] written = new boolean[BiomeColours.MAX_ROWS];
    private final ByteBuffer scratch =
            ByteBuffer.allocateDirect(BIOME_STRIDE * TEXEL_BYTES).order(ByteOrder.nativeOrder());
    private final IntBuffer colours = scratch.asIntBuffer();

    private int biomes;

    private TintTable(GpuBuffer buffer) {
        this.buffer = buffer;
    }

    public static TintTable create() {
        RenderSystem.assertOnRenderThread();
        GpuBuffer buffer = RenderSystem.getDevice()
                .createBuffer(() -> LABEL, USAGE, (long) BiomeColours.MAX_ROWS * BIOME_STRIDE * TEXEL_BYTES);
        return new TintTable(buffer);
    }

    public GpuBuffer buffer() {
        return buffer;
    }

    public boolean holds(int row) {
        return written[row];
    }

    public void writeRow(int row, BiomeColours source, Dictionary<String> names) {
        RenderSystem.assertOnRenderThread();
        appendBiomes(source, names);
        written[row] = true;
        write(row, 0, biomes, source, names);
    }

    public void appendBiomes(BiomeColours source, Dictionary<String> names) {
        RenderSystem.assertOnRenderThread();
        int grown = Math.min(names.size(), BIOME_STRIDE);
        if (grown <= biomes) {
            return;
        }

        for (int row = 0; row < BiomeColours.MAX_ROWS; row++) {
            if (written[row]) {
                write(row, biomes, grown, source, names);
            }
        }

        biomes = grown;
    }

    @Override
    public void close() {
        buffer.close();
    }

    private void write(int row, int from, int to, BiomeColours source, Dictionary<String> names) {
        if (to <= from) {
            return;
        }

        colours.clear();
        for (int biome = from; biome < to; biome++) {
            colours.put(source.colour(row, names.value(biome)));
        }

        long offset = ((long) row * BIOME_STRIDE + from) * TEXEL_BYTES;
        int length = (to - from) * TEXEL_BYTES;
        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(buffer.slice(offset, length), scratch.clear().limit(length));
    }
}
