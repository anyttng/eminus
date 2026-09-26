package com.eminus.model;

import java.util.ArrayList;
import java.util.List;

import com.eminus.mixin.StairBlockAccessor;
import com.eminus.model.port.BlockModel;
import com.eminus.model.port.BlockModels;
import com.eminus.model.port.BlockTints;
import com.eminus.model.port.ModelQuad;
import com.eminus.model.port.Variant;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import org.jspecify.annotations.Nullable;

public final class ModelBaker implements StateBaker {
    private static final long BAKE_SEED = 0L;
    private static final int ALPHA_MASK = 0xFF00_0000;

    private final BlockModels blockModels;
    private final BlockTints blockTints;
    private final FluidBaker fluids;
    private final SolidSprites sprites;
    private final BiomeColours colours;
    private final FaceRasterizer rasterizer = new FaceRasterizer();
    private final CountingRandom random = new CountingRandom();
    private final List<ModelQuad> quads = new ArrayList<>();

    private boolean swept;

    public ModelBaker(BlockModels blockModels, BlockTints blockTints, FluidBaker fluids, SolidSprites sprites,
            BiomeColours colours) {
        this.blockModels = blockModels;
        this.blockTints = blockTints;
        this.fluids = fluids;
        this.sprites = sprites;
        this.colours = colours;
    }

    @Override
    public BakedState bake(BlockState state) {
        if (!swept) {
            TintSweep.sweep(Block.BLOCK_STATE_REGISTRY, blockTints, fluids::tintSource, ModelBaker::baseOf, colours);
            swept = true;
        }

        BlockState shape = baseOf(state);
        BlockModel model = modelOf(shape);
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

        List<WeightedModel> variants = variants(model, shape, state);
        return new BakedState(block, surface, submerged, variants, variants.isEmpty());
    }

    @Override
    public void pick(BlockState state, RandomSource random, List<Object> parts) {
        blockModels.pick(baseOf(state), random, parts);
    }

    @Override
    public BakedModel bakeParts(BlockState state, List<Object> picked) {
        BlockState shape = baseOf(state);
        quads.clear();
        blockModels.quads(picked, quads);
        return quads.isEmpty() ? BakedModel.empty() : emissive(rasterize(shape), state);
    }

    private List<WeightedModel> variants(@Nullable BlockModel model, BlockState shape, BlockState state) {
        if (model == null || SeedOverrides.overridden(state.getBlock())) {
            return List.of();
        }

        List<Variant> entries = model.variants();
        if (entries.isEmpty()) {
            return List.of();
        }

        List<WeightedModel> baked = new ArrayList<>(entries.size());
        for (Variant entry : entries) {
            if (collect(entry.model()) || quads.isEmpty()) {
                return List.of();
            }

            BakedModel variant = emissive(rasterize(shape), state);
            if (!baked.isEmpty() && !baked.getFirst().model().sameGeometry(variant)) {
                return List.of();
            }

            baked.add(new WeightedModel(variant, entry.weight()));
        }

        return baked;
    }

    private BakedModel rasterize(BlockState shape) {
        return rasterizer.rasterize(quads, texels(shape),
                layer -> colours.resolve(blockTints.source(shape, layer), shape));
    }

    private BakedModel fluidModel(FluidState fluid, BlockState state) {
        return emissive(fluids.bake(fluid, colours.resolve(fluids.tintSource(fluid), state)), state);
    }

    private @Nullable BlockModel modelOf(BlockState shape) {
        return shape.getRenderShape() == RenderShape.INVISIBLE ? null : blockModels.model(shape);
    }

    private boolean collect(@Nullable BlockModel model) {
        quads.clear();
        random.restart(BAKE_SEED);
        if (model != null) {
            model.quads(random, quads);
        }

        return random.drew();
    }

    private QuadTexels texels(BlockState state) {
        return blockModels.forceOpaque(state)
                ? (quad, u, v) -> sprites.argb(quad.sprite(), u, v) | ALPHA_MASK
                : (quad, u, v) -> sprites.argb(quad.sprite(), u, v);
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

        return model.withMetadata(ModelMetadata.withEmission(model.metadata(), emission));
    }
}
