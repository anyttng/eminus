package com.eminus.client.render.far;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Set;

import com.eminus.client.frame.FaceShade;
import com.eminus.client.model.ModelAtlas;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.Std140;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.handoff.NearSections;
import com.eminus.render.far.CameraOrigin;

import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

public final class FarFrame implements AutoCloseable {
    public static final Std140.Block BLOCK = Std140.block("EminusFrame");

    private static final Std140.Member PROJ_VIEW = BLOCK.add(Std140.Type.MAT4, "EminusProjView");
    private static final Std140.Member VIEW = BLOCK.add(Std140.Type.MAT4, "EminusView");
    private static final Std140.Member MIN_BLOCK_Y = BLOCK.add(Std140.Type.INT, "EminusMinBlockY");
    private static final Std140.Member ATLAS_CELLS = BLOCK.add(Std140.Type.INT, "EminusAtlasCells");
    private static final Std140.Member ATLAS_CELL_SIDE = BLOCK.add(Std140.Type.INT, "EminusAtlasCellSide");
    private static final Std140.Member ATLAS_MEAN_LEVEL = BLOCK.add(Std140.Type.INT, "EminusAtlasMeanLevel");
    private static final Std140.Member NEAR_SIDE = BLOCK.add(Std140.Type.INT, "EminusNearSide");
    private static final Std140.Member NEAR_HEIGHT = BLOCK.add(Std140.Type.INT, "EminusNearHeight");
    private static final Std140.Member NEAR_ORIGIN = BLOCK.add(Std140.Type.IVEC3, "EminusNearOrigin");
    private static final Std140.Member SHADE_DOWN = BLOCK.add(Std140.Type.FLOAT, "EminusShadeDown");
    private static final Std140.Member SHADE_UP = BLOCK.add(Std140.Type.FLOAT, "EminusShadeUp");
    private static final Std140.Member SHADE_NORTH = BLOCK.add(Std140.Type.FLOAT, "EminusShadeNorth");
    private static final Std140.Member SHADE_SOUTH = BLOCK.add(Std140.Type.FLOAT, "EminusShadeSouth");
    private static final Std140.Member SHADE_WEST = BLOCK.add(Std140.Type.FLOAT, "EminusShadeWest");
    private static final Std140.Member SHADE_EAST = BLOCK.add(Std140.Type.FLOAT, "EminusShadeEast");
    private static final Std140.Member CAMERA_BLOCK_POS = BLOCK.add(Std140.Type.IVEC3, "EminusCameraBlockPos");
    private static final Std140.Member CAMERA_OFFSET = BLOCK.add(Std140.Type.VEC3, "EminusCameraOffset");
    private static final int SIZE = BLOCK.size();

    private static final String LABEL = "eminus-far-frame";
    private static final Set<BufferUsage> USAGE = EnumSet.of(BufferUsage.UNIFORM, BufferUsage.COPY_DST);
    private static final long START_OF_BUFFER = 0L;

    private final Gpu gpu;
    private final Buffer buffer;

    private FarFrame(Gpu gpu, Buffer buffer) {
        this.gpu = gpu;
        this.buffer = buffer;
    }

    public static FarFrame create(Gpu gpu) {
        gpu.assertRenderThread();
        return new FarFrame(gpu, gpu.buffer(LABEL, USAGE, SIZE));
    }

    public Buffer buffer() {
        return buffer;
    }

    public void write(Matrix4fc viewProjection, Matrix4fc view, int minBlockY, ModelAtlas atlas, NearSections near,
            FaceShade shade, CameraOrigin camera) {
        gpu.assertRenderThread();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer written = BLOCK.into(stack.malloc(SIZE))
                    .putMat4(PROJ_VIEW, viewProjection)
                    .putMat4(VIEW, view)
                    .putInt(MIN_BLOCK_Y, minBlockY)
                    .putInt(ATLAS_CELLS, atlas.cellsPerSide())
                    .putInt(ATLAS_CELL_SIDE, atlas.cellSide())
                    .putInt(ATLAS_MEAN_LEVEL, atlas.meanLevel())
                    .putInt(NEAR_SIDE, near.side())
                    .putInt(NEAR_HEIGHT, near.height())
                    .putIVec3(NEAR_ORIGIN, near.originBlockX(), near.originBlockY(), near.originBlockZ())
                    .putFloat(SHADE_DOWN, shade.down())
                    .putFloat(SHADE_UP, shade.up())
                    .putFloat(SHADE_NORTH, shade.north())
                    .putFloat(SHADE_SOUTH, shade.south())
                    .putFloat(SHADE_WEST, shade.west())
                    .putFloat(SHADE_EAST, shade.east())
                    .putIVec3(CAMERA_BLOCK_POS, camera.blockX(), camera.blockY(), camera.blockZ())
                    .putVec3(CAMERA_OFFSET, camera.offsetX(), camera.offsetY(), camera.offsetZ())
                    .get();
            gpu.write(buffer, START_OF_BUFFER, written);
        }
    }

    @Override
    public void close() {
        buffer.close();
    }
}
