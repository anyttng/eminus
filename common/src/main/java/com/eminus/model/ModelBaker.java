package com.eminus.model;

import java.util.ArrayList;
import java.util.List;

import com.eminus.mixin.StairBlockAccessor;
import com.eminus.mixin.WeightedVariantsAccessor;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.WeightedVariants;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import org.jspecify.annotations.Nullable;

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
    private final CountingRandom random = new CountingRandom();
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
        BlockStateModel model = modelOf(shape);
        boolean drew = collect(model);
        FluidState fluid = state.getFluidState();

        if (quads.isEmpty()) {
            if (fluid.isEmpty()) {
                return new BakedState(BakedModel.empty(), null);
            }

            BakedModel surface = fluidModel(fluid, state);
            return new BakedState(surface, null, FluidBaker.submerged(surface));
        }

        BakedModel block = emissive(rasterize(shape), state);
        BakedModel surface = fluid.isEmpty() ? null : fluidModel(fluid, state);
        BakedModel submerged = surface == null ? null : FluidBaker.submerged(surface);
        if (!drew) {
            return new BakedState(block, surface, submerged);
        }

        List<Weighted<BakedModel>> variants = variants(model, shape, state);
        return new BakedState(block, surface, submerged, variants, variants.isEmpty());
    }

    @Override
    public void pick(BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        blockModels.get(baseOf(state)).collectParts(random, parts);
    }

    @Override
    public BakedModel bakeParts(BlockState state, List<BlockStateModelPart> picked) {
        BlockState shape = baseOf(state);
        gather(picked);
        return quads.isEmpty() ? BakedModel.empty() : emissive(rasterize(shape), state);
    }

    private List<Weighted<BakedModel>> variants(@Nullable BlockStateModel model, BlockState shape, BlockState state) {
        if (!(model instanceof WeightedVariants weighted) || SeedOverrides.overridden(state.getBlock())) {
            return List.of();
        }

        List<Weighted<BlockStateModel>> entries = ((WeightedVariantsAccessor) weighted).eminus$list().unwrap();
        List<Weighted<BakedModel>> baked = new ArrayList<>(entries.size());
        for (Weighted<BlockStateModel> entry : entries) {
            if (collect(entry.value()) || quads.isEmpty()) {
                return List.of();
            }

            BakedModel variant = emissive(rasterize(shape), state);
            if (!baked.isEmpty() && !baked.getFirst().value().sameGeometry(variant)) {
                return List.of();
            }

            baked.add(new Weighted<>(variant, entry.weight()));
        }

        return baked;
    }

    private BakedModel rasterize(BlockState shape) {
        return rasterizer.rasterize(quads, texels(shape),
                layer -> colours.resolve(blockColors.getTintSource(shape, layer), shape));
    }

    private BakedModel fluidModel(FluidState fluid, BlockState state) {
        return emissive(fluids.bake(fluid, colours.resolve(fluids.tintSource(fluid), state)), state);
    }

    private @Nullable BlockStateModel modelOf(BlockState shape) {
        return shape.getRenderShape() == RenderShape.INVISIBLE ? null : blockModels.get(shape);
    }

    private boolean collect(@Nullable BlockStateModel model) {
        parts.clear();
        random.restart(BAKE_SEED);
        if (model != null) {
            model.collectParts(random, parts);
        }

        gather(parts);
        return random.drew();
    }

    private void gather(List<BlockStateModelPart> collected) {
        quads.clear();
        for (BlockStateModelPart part : collected) {
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
