package com.eminus.client.handoff;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.compat.sodium.SodiumDrawnSections;
import com.eminus.compat.sodium.SodiumMixinPlugin;
import com.eminus.handoff.NearSections;
import com.eminus.mesh.CellMesh;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

public final class NearSectionTable implements AutoCloseable {
    private static final String LABEL = "eminus-near-sections";
    private static final int USAGE = GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST;

    private final NearSections sections = new NearSections();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final NearSections.SectionQuery query = this::owned;
    private final boolean sodium = SodiumMixinPlugin.sodiumPresent();

    private GpuBuffer buffer;
    private ByteBuffer scratch;
    private IntBuffer texels;
    private LevelRenderer levelRenderer;
    private long sectionFadeMillis;
    private int cameraSectionX;
    private int cameraSectionY;
    private int cameraSectionZ;
    private int viewDistance;

    private NearSectionTable() {
        allocate(Integer.BYTES);
    }

    public static NearSectionTable create() {
        RenderSystem.assertOnRenderThread();
        return new NearSectionTable();
    }

    public NearSections sections() {
        return sections;
    }

    public GpuBuffer buffer() {
        return buffer;
    }

    public void fill(LevelRenderer renderer, long sectionFadeMillis, List<CellMesh> translucent, CellFrame frame,
            int cameraSectionX, int cameraSectionY, int cameraSectionZ, int viewDistance, int radius, int minSectionY,
            int sectionCount) {
        RenderSystem.assertOnRenderThread();
        levelRenderer = renderer;
        this.sectionFadeMillis = sectionFadeMillis;
        this.cameraSectionX = cameraSectionX;
        this.cameraSectionY = cameraSectionY;
        this.cameraSectionZ = cameraSectionZ;
        this.viewDistance = viewDistance;
        sections.reset(cameraSectionX, cameraSectionZ, radius, minSectionY, sectionCount);

        for (CellMesh mesh : translucent) {
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
                sectionY * NearSections.SECTION_BLOCKS, sectionZ * NearSections.SECTION_BLOCKS), sectionFadeMillis)
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
        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(buffer.slice(0, bytes), scratch.clear().limit(bytes));
    }

    private void allocate(int bytes) {
        buffer = RenderSystem.getDevice().createBuffer(() -> LABEL, USAGE, bytes);
        scratch = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        texels = scratch.asIntBuffer();
    }
}
