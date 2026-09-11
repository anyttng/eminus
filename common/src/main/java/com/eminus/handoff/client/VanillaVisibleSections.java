package com.eminus.handoff.client;

import com.eminus.handoff.CoverageSections;
import com.eminus.handoff.VisibleSections;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;

public final class VanillaVisibleSections implements VisibleSections {
    private final LevelRenderer renderer;

    public VanillaVisibleSections(LevelRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void collect(CoverageSections into) {
        ObjectArrayList<SectionRenderDispatcher.RenderSection> visible = renderer.visibleSections();

        for (int i = 0; i < visible.size(); i++) {
            SectionRenderDispatcher.RenderSection section = visible.get(i);
            if (section.getSectionMesh() != CompiledSectionMesh.UNCOMPILED) {
                BlockPos origin = section.getRenderOrigin();
                into.add(origin.getX(), origin.getY(), origin.getZ());
            }
        }
    }
}
