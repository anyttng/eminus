package com.eminus.model;

import java.util.ArrayList;
import java.util.List;

import com.eminus.mixin.StairBlockAccessor;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class ModelBaker implements StateBaker {
    private static final long BAKE_SEED = 0L;
    private static final int ALPHA_MASK = 0xFF00_0000;
    private static final Direction[] FACES = Direction.values();

    private final BlockStateModelSet blockModels;
    private final BlockColors blockColors;
    private final FluidBaker fluids;
    private final SolidSprites sprites;
    private final BiomeColours colours;
    private final boolean cutoutLeaves;
    private final FaceRasterizer rasterizer = new FaceRasterizer();
    private final RandomSource random = RandomSource.create();
    private final List<BlockStateModelPart> parts = new ArrayList<>();
    private final List<BakedQuad> quads = new ArrayList<>();

    private boolean swept;

    public ModelBaker(BlockStateModelSet blockModels, BlockColors blockColors, FluidBaker fluids,
            SolidSprites sprites, BiomeColours colours, boolean cutoutLeaves) {
        this.blockModels = blockModels;
        this.blockColors = blockColors;
        this.fluids = fluids;
        this.sprites = sprites;
        this.colours = colours;
        this.cutoutLeaves = cutoutLeaves;
    }

    @Override
    public BakedState bake(BlockState state) {
        if (!swept) {
            TintSweep.sweep(Block.BLOCK_STATE_REGISTRY, blockColors, fluids::tintSource, ModelBaker::baseOf, colours);
            swept = true;
        }

        BlockState shape = baseOf(state);
        collect(shape);
        FluidState fluid = state.getFluidState();

        if (quads.isEmpty()) {
            return new BakedState(fluid.isEmpty() ? BakedModel.empty() : fluidModel(fluid, state), null);
        }

        BakedModel model = rasterizer.rasterize(quads, texels(shape),
                layer -> colours.resolve(blockColors.getTintSource(shape, layer), shape));
        return new BakedState(emissive(model, state), fluid.isEmpty() ? null : fluidModel(fluid, state));
    }

    private BakedModel fluidModel(FluidState fluid, BlockState state) {
        return emissive(fluids.bake(fluid, colours.resolve(fluids.tintSource(fluid), state)), state);
    }

    private void collect(BlockState state) {
        quads.clear();
        if (state.getRenderShape() == RenderShape.INVISIBLE) {
            return;
        }

        parts.clear();
        random.setSeed(BAKE_SEED);
        blockModels.get(state).collectParts(random, parts);

        for (BlockStateModelPart part : parts) {
            quads.addAll(part.getQuads(null));
            for (Direction face : FACES) {
                quads.addAll(part.getQuads(face));
            }
        }
    }

    private QuadTexels texels(BlockState state) {
        return ModelBlockRenderer.forceOpaque(cutoutLeaves, state)
                ? (quad, u, v) -> sprites.argb(quad.materialInfo().sprite(), u, v) | ALPHA_MASK
                : (quad, u, v) -> sprites.argb(quad.materialInfo().sprite(), u, v);
    }

    private static BlockState baseOf(BlockState state) {
        return state.getBlock() instanceof StairBlock stairs
                ? ((StairBlockAccessor) stairs).eminus$baseState()
                : state;
    }

    private static BakedModel emissive(BakedModel model, BlockState state) {
        int baked = ModelMetadata.emission(model.metadata());
        int emission = Math.max(baked, state.getLightEmission());
        if (emission == baked) {
            return model;
        }

        return new BakedModel(model.faces(), model.tintMask(), model.insets(), model.bounds(),
                ModelMetadata.withEmission(model.metadata(), emission), model.tintRow());
    }
}
