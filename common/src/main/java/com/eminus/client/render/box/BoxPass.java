package com.eminus.client.render.box;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

import com.eminus.Eminus;
import com.eminus.box.BoxRecords;
import com.eminus.box.BoxRegistry;
import com.eminus.box.BoxSnapshot;
import com.eminus.client.frame.FaceShade;
import com.eminus.client.frame.GameFog;
import com.eminus.client.frame.GameFrame;
import com.eminus.client.render.far.FarTarget;
import com.eminus.gpu.Format;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.Location;
import com.eminus.gpu.Std140;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.gpu.buffer.TexelView;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pass.PassSpec;
import com.eminus.gpu.pipeline.Binding;
import com.eminus.gpu.pipeline.Blend;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;
import com.eminus.render.backend.DepthConvention;
import com.eminus.render.far.CameraOrigin;

import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

public final class BoxPass implements AutoCloseable {
    public static final Std140.Block BLOCK = Std140.block("Boxes");
    public static final Format TEXEL_FORMAT = Format.RGBA32_UINT;

    private static final Std140.Member PROJ_VIEW = BLOCK.add(Std140.Type.MAT4, "BoxProjView");
    private static final Std140.Member VIEW = BLOCK.add(Std140.Type.MAT4, "BoxView");
    private static final Std140.Member FOG_COLOUR = BLOCK.add(Std140.Type.VEC4, "BoxFogColour");
    private static final Std140.Member CAMERA_BLOCK_POS = BLOCK.add(Std140.Type.IVEC3, "BoxCameraBlockPos");
    private static final Std140.Member CAMERA_OFFSET = BLOCK.add(Std140.Type.VEC3, "BoxCameraOffset");
    private static final Std140.Member SHADE_DOWN = BLOCK.add(Std140.Type.FLOAT, "BoxShadeDown");
    private static final Std140.Member SHADE_UP = BLOCK.add(Std140.Type.FLOAT, "BoxShadeUp");
    private static final Std140.Member SHADE_NORTH = BLOCK.add(Std140.Type.FLOAT, "BoxShadeNorth");
    private static final Std140.Member SHADE_SOUTH = BLOCK.add(Std140.Type.FLOAT, "BoxShadeSouth");
    private static final Std140.Member SHADE_WEST = BLOCK.add(Std140.Type.FLOAT, "BoxShadeWest");
    private static final Std140.Member SHADE_EAST = BLOCK.add(Std140.Type.FLOAT, "BoxShadeEast");
    private static final Std140.Member NEAR_PLANE = BLOCK.add(Std140.Type.FLOAT, "BoxNearPlane");
    private static final Std140.Member FOG_START = BLOCK.add(Std140.Type.FLOAT, "BoxFogStart");
    private static final Std140.Member FOG_END = BLOCK.add(Std140.Type.FLOAT, "BoxFogEnd");
    private static final int SIZE = BLOCK.size();

    private static final Location SHADER = new Location(Eminus.MODID, "core/far_boxes");
    private static final Location FAR_PIPELINE = new Location(Eminus.MODID, "far_boxes");
    private static final Location NEAR_PIPELINE = new Location(Eminus.MODID, "near_boxes");
    private static final String FAR_LABEL = "eminus-far-boxes";
    private static final String NEAR_LABEL = "eminus-near-boxes";
    private static final String RECORDS_LABEL = "eminus-box-records";
    private static final String RECORDS = "BoxRecords";
    private static final String LIGHTMAP = "Lightmap";
    private static final Set<BufferUsage> UNIFORM_USAGE = EnumSet.of(BufferUsage.UNIFORM, BufferUsage.COPY_DST);
    private static final Set<BufferUsage> RECORD_USAGE = EnumSet.of(BufferUsage.TEXEL, BufferUsage.COPY_DST);
    private static final Vector4fc NO_FOG = new Vector4f();
    private static final float NO_NEAR_PLANE = 0.0F;
    private static final long START_OF_BUFFER = 0L;
    private static final long NOTHING_UPLOADED = -1L;

    private final Gpu gpu;
    private final Pipeline far;
    private final Pipeline near;
    private final Buffer farUniform;
    private final Buffer nearUniform;

    private @Nullable Buffer records;
    private @Nullable TexelView texels;
    private ByteBuffer scratch = ByteBuffer.allocateDirect(0);
    private long version = NOTHING_UPLOADED;
    private int count;

    private BoxPass(Gpu gpu, Pipeline far, Pipeline near, Buffer farUniform, Buffer nearUniform) {
        this.gpu = gpu;
        this.far = far;
        this.near = near;
        this.farUniform = farUniform;
        this.nearUniform = nearUniform;
    }

    public static BoxPass create(Gpu gpu, DepthConvention depth) {
        gpu.assertRenderThread();
        return new BoxPass(gpu,
                gpu.pipeline(pipeline(FAR_PIPELINE, depth, gpu, FarTarget.COLOUR_FORMAT).build()),
                gpu.pipeline(pipeline(NEAR_PIPELINE, depth, gpu, gpu.mainColour().format())
                        .withDefine("NEAR_RANGE").build()),
                gpu.buffer(FAR_LABEL, UNIFORM_USAGE, SIZE), gpu.buffer(NEAR_LABEL, UNIFORM_USAGE, SIZE));
    }

    public List<Pipeline> pipelines() {
        return List.of(far, near);
    }

    public boolean drawsAny() {
        return count > 0;
    }

    public void update(BoxRegistry registry, String dimension) {
        gpu.assertRenderThread();
        if (registry.version() == version) {
            return;
        }

        BoxSnapshot snapshot = registry.snapshot(dimension);
        version = snapshot.version();
        count = snapshot.boxes().size();
        if (count == 0) {
            return;
        }

        long bytes = BoxRecords.bytes(count);
        if (records == null || records.size() < bytes) {
            long capacity = records == null ? bytes : Math.max(bytes, records.size() * 2);
            closeRecords();
            records = gpu.buffer(RECORDS_LABEL, RECORD_USAGE, capacity);
            texels = gpu.texelView(records, TEXEL_FORMAT);
        }
        if (scratch.capacity() < bytes) {
            scratch = ByteBuffer.allocateDirect(Math.toIntExact(bytes)).order(ByteOrder.nativeOrder());
        }

        scratch.clear();
        BoxRecords.write(snapshot.boxes(), scratch);
        scratch.flip();
        gpu.write(records, START_OF_BUFFER, scratch);
    }

    public void drawFar(FarTarget target, Matrix4fc farViewProjection, GameFrame game) {
        if (count == 0) {
            return;
        }

        write(farUniform, farViewProjection, game, NO_NEAR_PLANE, NO_FOG, 0.0F, 0.0F);
        draw(far, farUniform, PassSpec.of(FAR_LABEL, target.colour(), null)
                .withDepth(target.depth(), OptionalDouble.empty()));
    }

    public void drawNear(Texture colour, Texture depth, Matrix4fc gameViewProjection, GameFrame game,
            float nearPlane) {
        if (count == 0) {
            return;
        }

        GameFog fog = game.fog();
        write(nearUniform, gameViewProjection, game, nearPlane, fog.colour(), fog.environmentalStart(),
                fog.environmentalEnd());
        draw(near, nearUniform, PassSpec.of(NEAR_LABEL, colour, null).withDepth(depth, OptionalDouble.empty()));
    }

    @Override
    public void close() {
        closeRecords();
        farUniform.close();
        nearUniform.close();
    }

    private void draw(Pipeline pipeline, Buffer uniform, PassSpec spec) {
        gpu.assertRenderThread();
        try (Pass pass = gpu.pass(spec)) {
            pass.pipeline(pipeline);
            pass.bind(BLOCK.name(), uniform);
            pass.bind(RECORDS, texels);
            pass.bind(LIGHTMAP, gpu.lightmap(), Sampler.LINEAR);
            pass.draw(count * BoxRecords.VERTICES);
        }
    }

    private void write(Buffer uniform, Matrix4fc viewProjection, GameFrame game, float nearPlane,
            Vector4fc fogColour, float fogStart, float fogEnd) {
        CameraOrigin camera = CameraOrigin.of(game.eyeX(), game.eyeY(), game.eyeZ());
        FaceShade shade = game.shade();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer written = BLOCK.into(stack.malloc(SIZE))
                    .putMat4(PROJ_VIEW, viewProjection)
                    .putMat4(VIEW, game.viewRotation())
                    .putVec4(FOG_COLOUR, fogColour)
                    .putIVec3(CAMERA_BLOCK_POS, camera.blockX(), camera.blockY(), camera.blockZ())
                    .putVec3(CAMERA_OFFSET, camera.offsetX(), camera.offsetY(), camera.offsetZ())
                    .putFloat(SHADE_DOWN, shade.down())
                    .putFloat(SHADE_UP, shade.up())
                    .putFloat(SHADE_NORTH, shade.north())
                    .putFloat(SHADE_SOUTH, shade.south())
                    .putFloat(SHADE_WEST, shade.west())
                    .putFloat(SHADE_EAST, shade.east())
                    .putFloat(NEAR_PLANE, nearPlane)
                    .putFloat(FOG_START, fogStart)
                    .putFloat(FOG_END, fogEnd)
                    .get();
            gpu.write(uniform, START_OF_BUFFER, written);
        }
    }

    private void closeRecords() {
        if (texels != null) {
            texels.close();
            texels = null;
        }
        if (records != null) {
            records.close();
            records = null;
        }
    }

    private static PipelineSpec.Builder pipeline(Location location, DepthConvention depth, Gpu gpu,
            Format colourFormat) {
        PipelineSpec.Builder builder = PipelineSpec.builder(location, SHADER, SHADER)
                .withBinding(Binding.uniform(BLOCK.name()))
                .withBinding(Binding.texel(RECORDS, TEXEL_FORMAT))
                .withBinding(Binding.sampled(LIGHTMAP))
                .withDefine("BOX_VERTICES", BoxRecords.VERTICES)
                .withDefine("BOX_TEXELS", BoxRecords.TEXELS)
                .withDefine("BOX_EMISSIVE", BoxRecords.EMISSIVE)
                .withColourTarget(colourFormat, Blend.TRANSLUCENT, true)
                .withDepthTest(depth.compare(), true);
        return gpu.capabilities().lightmapHalfTexel() ? builder.withDefine("LIGHTMAP_HALF_TEXEL") : builder;
    }
}
