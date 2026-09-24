package com.eminus.client.handoff;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.compat.sodium.SodiumDrawnSections;
import com.eminus.compat.sodium.SodiumMixinPlugin;
import com.eminus.gpu.Gpu;
import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.BufferUsage;
import com.eminus.handoff.NearSections;
import com.eminus.mesh.MeshSummary;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

public final class NearSectionTable implements AutoCloseable {
    private static final String LABEL = "eminus-near-sections";
    private static final Set<BufferUsage> USAGE = EnumSet.of(BufferUsage.TEXEL, BufferUsage.COPY_DST);
    private static final long START_OF_BUFFER = 0L;

    private final Gpu gpu;
    private final NearSections sections = new NearSections();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final NearSections.SectionQuery query = this::owned;
    private final boolean sodium = SodiumMixinPlugin.sodiumPresent();

    private Buffer buffer;
    private ByteBuffer scratch;
    private IntBuffer texels;
    private LevelRenderer levelRenderer;
    private int cameraSectionX;
    private int cameraSectionY;
    private int cameraSectionZ;
    private int viewDistance;

    private NearSectionTable(Gpu gpu) {
        this.gpu = gpu;
        allocate(Integer.BYTES);
    }

    public static NearSectionTable create(Gpu gpu) {
        gpu.assertRenderThread();
        return new NearSectionTable(gpu);
    }

    public NearSections sections() {
        return sections;
    }

    public Buffer buffer() {
        return buffer;
    }

    public void fill(LevelRenderer renderer, List<MeshSummary> meshes, CellFrame frame, int cameraSectionX,
            int cameraSectionY, int cameraSectionZ, int viewDistance, int radius, int minSectionY, int sectionCount) {
        gpu.assertRenderThread();
        levelRenderer = renderer;
        this.cameraSectionX = cameraSectionX;
        this.cameraSectionY = cameraSectionY;
        this.cameraSectionZ = cameraSectionZ;
        this.viewDistance = viewDistance;
        sections.reset(cameraSectionX, cameraSectionZ, radius, minSectionY, sectionCount);

        for (MeshSummary mesh : meshes) {
            long key = mesh.key();
            int level = CellKey.level(key);
            int side = DetailLevel.blocksPerCell(level);
            int minX = frame.originBlockX(CellKey.x(key), level);
            int minY = frame.originBlockY(CellKey.y(key), level);
            int minZ = frame.originBlockZ(CellKey.z(key), level);
            sections.queryBlocks(minX, minY, minZ, minX + side, minY + side, minZ + side, query);
        }

        levelRenderer = null;
        upload();
    }

    @Override
    public void close() {
        buffer.close();
    }

    private boolean owned(int sectionX, int sectionY, int sectionZ) {
        return levelRenderer.isSectionCompiledAndVisible(pos.set(sectionX * NearSections.SECTION_BLOCKS,
                sectionY * NearSections.SECTION_BLOCKS, sectionZ * NearSections.SECTION_BLOCKS))
                && drawn(sectionX, sectionY, sectionZ);
    }

    private boolean drawn(int sectionX, int sectionY, int sectionZ) {
        if (sodium) {
            return SodiumDrawnSections.drawn(sectionX, sectionY, sectionZ);
        }

        return NearSections.inVanillaViewDistance(cameraSectionX, cameraSectionY, cameraSectionZ, viewDistance,
                sectionX, sectionY, sectionZ);
    }

    private void upload() {
        int bytes = sections.texels() * Integer.BYTES;
        if (buffer.size() < bytes) {
            buffer.close();
            allocate(bytes);
        }

        texels.clear();
        texels.put(sections.owned(), 0, sections.texels());
        gpu.write(buffer, START_OF_BUFFER, scratch.clear().limit(bytes));
    }

    private void allocate(int bytes) {
        buffer = gpu.buffer(LABEL, USAGE, bytes);
        scratch = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        texels = scratch.asIntBuffer();
    }
}
