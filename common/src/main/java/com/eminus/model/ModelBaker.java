package com.eminus.model;

import java.util.ArrayList;
import java.util.List;

import com.eminus.mixin.StairBlockAccessor;
import com.eminus.model.port.BlockModel;
import com.eminus.model.port.BlockModels;
import com.eminus.model.port.BlockTints;
import com.eminus.model.port.ModelQuad;
import com.eminus.model.port.Variant;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class ModelBaker implements StateBaker {
    private static final long BAKE_SEED = 0L;

    private final BlockModels blockModels;
    private final BlockTints blockTints;
    private final FluidBaker fluids;
    private final SpriteTexels sprites;
    private final BiomeColors colors;
    private final boolean drawsBlades;
    private final FaceRasterizer rasterizer = new FaceRasterizer();
    private final CountingRandom random = new CountingRandom();
    private final List<ModelQuad> quads = new ArrayList<>();

    private boolean swept;

    public ModelBaker(BlockModels blockModels, BlockTints blockTints, FluidBaker fluids, SpriteTexels sprites,
            BiomeColors colors, boolean drawsBlades) {
        this.blockModels = blockModels;
        this.blockTints = blockTints;
        this.fluids = fluids;
        this.sprites = sprites;
        this.colors = colors;
        this.drawsBlades = drawsBlades;
    }

    @Override
    public BakedState bake(BlockState state) {
        return bake(state, false);
    }

    @Override
    public @Nullable BakedState bakeOpaqueTwin(BlockState state) {
        return twinned(state) ? bake(state, true) : null;
    }

    @Override
    public void pick(BlockState state, RandomSource random, List<Object> parts) {
        blockModels.pick(baseOf(state), random, parts);
    }

    @Override
    public BakedModel bakeParts(BlockState state, List<Object> picked) {
        return bakeParts(state, picked, false);
    }

    @Override
    public @Nullable BakedModel bakeOpaqueTwinParts(BlockState state, List<Object> picked) {
        return twinned(state) ? bakeParts(state, picked, true) : null;
    }

    private boolean twinned(BlockState state) {
        BlockState shape = baseOf(state);
        return blockModels.forceOpaqueWithOpaqueLeaves(shape) && !blockModels.forceOpaque(shape);
    }

    private BakedState bake(BlockState state, boolean opaque) {
        if (!swept) {
            TintSweep.sweep(Block.BLOCK_STATE_REGISTRY, blockTints, fluids::tintSource, ModelBaker::baseOf, colors);
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

        BakedModel block = blockModel(shape, state, opaque);
        BakedModel surface = fluid.isEmpty() ? null : fluidModel(fluid, state);
        BakedModel submerged = surface == null ? null : FluidBaker.submerged(surface);
        if (!drew) {
            return new BakedState(block, surface, submerged);
        }

        List<WeightedModel> variants = variants(model, shape, state, opaque);
        return new BakedState(block, surface, submerged, variants, variants.isEmpty());
    }

    private BakedModel bakeParts(BlockState state, List<Object> picked, boolean opaque) {
        BlockState shape = baseOf(state);
        quads.clear();
        blockModels.quads(picked, quads);
        return quads.isEmpty() ? BakedModel.empty() : blockModel(shape, state, opaque);
    }

    private List<WeightedModel> variants(@Nullable BlockModel model, BlockState shape, BlockState state,
            boolean opaque) {
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

            BakedModel variant = blockModel(shape, state, opaque);
            if (!baked.isEmpty() && !baked.getFirst().model().sameGeometry(variant)) {
                return List.of();
            }

            baked.add(new WeightedModel(variant, entry.weight()));
        }

        return baked;
    }

    private BakedModel blockModel(BlockState shape, BlockState state, boolean opaque) {
        BakedModel model = emissive(rasterize(shape, opaque), state);
        boolean smooth = !quads.isEmpty() && quads.getFirst().smooth() && state.getLightEmission() == 0;
        return smooth ? model.withMetadata(model.metadata() | ModelMetadata.SMOOTH) : model;
    }

    private BakedModel rasterize(BlockState shape, boolean opaque) {
        if (!drawsBlades && (FaceRasterizer.bladed(quads) || groundCover(shape, quads))) {
            return BakedModel.empty();
        }

        return rasterizer.rasterize(quads, (quad, u, v) -> sprites.argb(quad.sprite(), u, v),
                layer -> colors.resolve(blockTints.source(shape, layer), shape),
                opaque || blockModels.forceOpaque(shape));
    }

    private BakedModel fluidModel(FluidState fluid, BlockState state) {
        return emissive(fluids.bake(fluid, colors.resolve(fluids.tintSource(fluid), state)), state);
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

    static boolean groundCover(BlockState state, List<ModelQuad> quads) {
        if (!(state.getBlock() instanceof VegetationBlock)
                || !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
            return false;
        }

        boolean flat = false;
        for (ModelQuad quad : quads) {
            Vector3fc normal = FaceRasterizer.normal(quad);
            if (FaceRasterizer.horizontal(normal)) {
                flat = true;
            } else if (!FaceRasterizer.blade(normal)) {
                return false;
            }
        }

        return flat;
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
