package com.eminus.client.render.beam;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

import com.eminus.Eminus;
import com.eminus.beam.Beam;
import com.eminus.beam.BeamSet;
import com.eminus.beam.BeamSnapshot;
import com.eminus.beam.BeamTexels;
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
import com.eminus.render.backend.DepthConvention;
import com.eminus.render.far.CameraOrigin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

public final class BeamPass implements AutoCloseable {
    public static final Std140.Block BLOCK = Std140.block("Beams");
    public static final Format TEXEL_FORMAT = Format.RGBA32_UINT;

    private static final Std140.Member PROJ_VIEW = BLOCK.add(Std140.Type.MAT4, "BeamProjView");
    private static final Std140.Member CAMERA_BLOCK_POS = BLOCK.add(Std140.Type.IVEC3, "BeamCameraBlockPos");
    private static final Std140.Member CAMERA_OFFSET = BLOCK.add(Std140.Type.VEC3, "BeamCameraOffset");
    private static final Std140.Member SECTIONS = BLOCK.add(Std140.Type.INT, "BeamSections");
    private static final int SIZE = BLOCK.size();

    private static final float SOLID_RADIUS = 0.2F;
    private static final float GLOW_RADIUS = 0.25F;
    private static final double GAME_BEAM_REACH = 256.0;
    private static final float SCALE_DISTANCE = 96.0F;
    private static final int GLOW_ALPHA = 32;
    private static final int PRISMS = 2;
    private static final double BLOCK_CENTER = 0.5;

    private static final Location SHADER = new Location(Eminus.MODID, "core/far_beams");
    private static final Location PIPELINE = new Location(Eminus.MODID, "far_beams");
    private static final String LABEL = "eminus-far-beams";
    private static final String RECORDS_LABEL = "eminus-beam-records";
    private static final String RECORDS = "BeamRecords";
    private static final Set<BufferUsage> UNIFORM_USAGE = EnumSet.of(BufferUsage.UNIFORM, BufferUsage.COPY_DST);
    private static final Set<BufferUsage> RECORD_USAGE = EnumSet.of(BufferUsage.TEXEL, BufferUsage.COPY_DST);
    private static final long START_OF_BUFFER = 0L;

    private final Gpu gpu;
    private final Pipeline pipeline;
    private final Buffer uniform;

    private @Nullable Buffer records;
    private @Nullable TexelView texels;
    private ByteBuffer scratch = ByteBuffer.allocateDirect(0);
    private @Nullable BeamSnapshot held;
    private List<Beam> drawn = List.of();
    private int sections;

    private BeamPass(Gpu gpu, Pipeline pipeline, Buffer uniform) {
        this.gpu = gpu;
        this.pipeline = pipeline;
        this.uniform = uniform;
    }

    public static BeamPass create(Gpu gpu, DepthConvention depth) {
        gpu.assertRenderThread();
        PipelineSpec.Builder builder = PipelineSpec.builder(PIPELINE, SHADER, SHADER)
                .withBinding(Binding.uniform(BLOCK.name()))
                .withBinding(Binding.texel(RECORDS, TEXEL_FORMAT))
                .withDefine("BEAM_TEXELS", BeamTexels.TEXELS)
                .withDefine("BEAM_PRISM_VERTICES", BeamTexels.VERTICES_PER_PRISM)
                .withDefine("BEAM_SOLID_RADIUS", SOLID_RADIUS)
                .withDefine("BEAM_GLOW_RADIUS", GLOW_RADIUS)
                .withDefine("BEAM_SCALE_DISTANCE", SCALE_DISTANCE)
                .withDefine("BEAM_GLOW_ALPHA", GLOW_ALPHA)
                .withColorTarget(FarTarget.COLOR_FORMAT, Blend.TRANSLUCENT, true)
                .withDepthTest(depth.compare(), true);
        return new BeamPass(gpu, gpu.pipeline(builder.build()), gpu.buffer(LABEL, UNIFORM_USAGE, SIZE));
    }

    public Pipeline pipeline() {
        return pipeline;
    }

    public boolean drawsAny() {
        return sections > 0;
    }

    public void update(BeamSet beams, ClientLevel level, GameFrame game) {
        gpu.assertRenderThread();
        BeamSnapshot snapshot = held;
        if (snapshot == null || snapshot.version() != beams.version()) {
            snapshot = beams.snapshot();
            held = snapshot;
        }

        List<Beam> unseen = unseenByTheGame(snapshot.beams(), level, game);
        if (unseen.equals(drawn)) {
            return;
        }

        drawn = unseen;
        sections = BeamTexels.sections(unseen);
        if (sections == 0) {
            return;
        }

        reserve(BeamTexels.bytes(sections));
        scratch.clear();
        BeamTexels.write(unseen, BeaconRenderer.MAX_RENDER_Y, scratch);
        scratch.flip();
        gpu.write(Objects.requireNonNull(records, "reserved above"), START_OF_BUFFER, scratch);
    }

    // The game draws a beam whose chunk the client holds within a fixed 256 blocks (BeaconRenderer.shouldRender).
    private static List<Beam> unseenByTheGame(List<Beam> beams, ClientLevel level, GameFrame game) {
        List<Beam> unseen = new ArrayList<>(beams.size());
        for (Beam beam : beams) {
            int x = BlockPos.getX(beam.pos());
            int z = BlockPos.getZ(beam.pos());
            double dx = x + BLOCK_CENTER - game.eyeX();
            double dz = z + BLOCK_CENTER - game.eyeZ();
            boolean held = level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(x),
                    SectionPos.blockToSectionCoord(z));
            if (!held || dx * dx + dz * dz >= GAME_BEAM_REACH * GAME_BEAM_REACH) {
                unseen.add(beam);
            }
        }

        return List.copyOf(unseen);
    }

    private void reserve(long bytes) {
        Buffer target = records;
        if (target == null || target.size() < bytes) {
            long capacity = target == null ? bytes : Math.max(bytes, target.size() * 2);
            closeRecords();
            target = gpu.buffer(RECORDS_LABEL, RECORD_USAGE, capacity);
            records = target;
            texels = gpu.texelView(target, TEXEL_FORMAT);
        }
        if (scratch.capacity() < bytes) {
            scratch = ByteBuffer.allocateDirect(Math.toIntExact(bytes)).order(ByteOrder.nativeOrder());
        }
    }

    public void draw(FarTarget target, Matrix4fc farViewProjection, GameFrame game) {
        gpu.assertRenderThread();
        if (sections == 0) {
            return;
        }

        CameraOrigin camera = CameraOrigin.of(game.eyeX(), game.eyeY(), game.eyeZ());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer written = BLOCK.into(stack.malloc(SIZE))
                    .putMat4(PROJ_VIEW, farViewProjection)
                    .putIVec3(CAMERA_BLOCK_POS, camera.blockX(), camera.blockY(), camera.blockZ())
                    .putVec3(CAMERA_OFFSET, camera.offsetX(), camera.offsetY(), camera.offsetZ())
                    .putInt(SECTIONS, sections)
                    .get();
            gpu.write(uniform, START_OF_BUFFER, written);
        }

        try (Pass pass = gpu.pass(PassSpec.of(LABEL, target.color(), null)
                .withDepth(target.depth(), OptionalDouble.empty()))) {
            pass.pipeline(pipeline);
            pass.bind(BLOCK.name(), uniform);
            pass.bind(RECORDS, Objects.requireNonNull(texels, "beams are drawn only once records hold them"));
            pass.draw(sections * PRISMS * BeamTexels.VERTICES_PER_PRISM);
        }
    }

    @Override
    public void close() {
        closeRecords();
        uniform.close();
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
}
