package com.eminus.client.gpu.game;

import java.util.Optional;

import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.TexelView;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pass.PassSpec;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.texture.Sampler;
import com.eminus.gpu.texture.Texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

final class GamePass implements Pass {
    private static final String GLOBALS_UNIFORM = "Globals";
    private static final int INSTANCES = 1;
    private static final int FIRST_VERTEX = 0;
    private static final int FIRST_INSTANCE = 0;

    private final RenderPass pass;

    private GamePass(RenderPass pass) {
        this.pass = pass;
    }

    static GamePass open(PassSpec spec) {
        GameTexture colour = (GameTexture) spec.colour();
        RenderPassDescriptor.Builder descriptor = RenderPassDescriptor.builder(spec::label)
                .withColorAttachment(colour.view(), Optional.ofNullable(spec.clearColour()));
        if (spec.depth() != null) {
            descriptor.withDepthAttachment(((GameTexture) spec.depth()).view(), spec.clearDepth());
        }
        descriptor.withRenderArea(new RenderPass.RenderArea(0, 0, colour.width(), colour.height()));

        return new GamePass(RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor.build()));
    }

    @Override
    public void pipeline(Pipeline pipeline) {
        pass.setPipeline(RenderSystem.getCompiledPipeline(((GamePipeline) pipeline).pipeline()));
    }

    @Override
    public void bind(String name, Buffer uniform) {
        pass.setUniform(name, GameTypes.buffer(uniform));
    }

    @Override
    public void bind(String name, TexelView texels) {
        pass.setUniform(name, GameTypes.buffer(texels.buffer()));
    }

    @Override
    public void bind(String name, Texture texture, Sampler sampler) {
        pass.setUniform(name, ((GameTexture) texture).view(), GameTypes.sampler(sampler));
    }

    @Override
    public void bindGameGlobals() {
        pass.setUniform(GLOBALS_UNIFORM, RenderSystem.getGlobalSettingsUniform());
    }

    @Override
    public void quadIndices(int maxIndices) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        pass.setIndexBuffer(indices.getBuffer(maxIndices), indices.type());
    }

    @Override
    public void draw(int vertices) {
        pass.draw(vertices, INSTANCES, FIRST_VERTEX, FIRST_INSTANCE);
    }

    @Override
    public void drawIndexedIndirect(Buffer commands, int firstCommand, int count) {
        pass.drawIndexedIndirect(GameTypes.indexedIndirect(commands, firstCommand, count), count);
    }

    @Override
    public void close() {
        pass.close();
    }
}
