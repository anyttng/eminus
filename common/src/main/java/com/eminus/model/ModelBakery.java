package com.eminus.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

import com.eminus.Eminus;
import com.eminus.cell.Dictionary;
import com.eminus.cell.IdTable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

import org.jspecify.annotations.Nullable;

public final class ModelBakery implements ModelSource {
    public static final String THREAD_NAME = "eminus-bakery";
    public static final int MISSING = Dictionary.MISSING;
    public static final int NO_FLUID = -2;
    public static final int POSITIONAL = -4;
    public static final int PLACEHOLDER_COLOUR = 0xFFFF_00FF;
    public static final ToIntFunction<BlockState> NO_PACK_IDS = state -> BakedModel.NO_PACK_ID;

    private static final int THREAD_PRIORITY = Thread.NORM_PRIORITY - 1;
    private static final long LOW_WORD = 0xFFFF_FFFFL;

    private record Request(BlockState state, @Nullable List<Object> parts) {
    }

    private static final class Picker {
        private final SingleThreadedRandomSource random = new SingleThreadedRandomSource(0L);
        private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        private final List<Object> parts = new ArrayList<>();
    }

    private final StateBaker baker;
    private final ToIntFunction<BlockState> packIds;
    private final Dictionary<BakedModel> models = new Dictionary<>((id, model) -> { });
    private final Map<BlockState, Integer> idByState = new ConcurrentHashMap<>();
    private final Map<BlockState, Integer> fluidIdByState = new ConcurrentHashMap<>();
    private final Set<BlockState> positionalStates = ConcurrentHashMap.newKeySet();
    private final Map<Request, Integer> idByParts = new ConcurrentHashMap<>();
    private final IdTable submergedIds = new IdTable(0, MISSING);
    private final Map<Integer, Integer> oneSidedIds = new ConcurrentHashMap<>();
    private final Map<Long, Integer> inwardIds = new ConcurrentHashMap<>();
    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private final Map<Request, List<Runnable>> waiting = new HashMap<>();
    private final ThreadLocal<Picker> pickers = ThreadLocal.withInitial(Picker::new);
    private final Thread thread = new Thread(this::serve, THREAD_NAME);
    private final BakedModel placeholder = BakedModel.solid(PLACEHOLDER_COLOUR);

    private volatile boolean running = true;

    private ModelBakery(StateBaker baker, ToIntFunction<BlockState> packIds) {
        this.baker = baker;
        this.packIds = packIds;
    }

    public static ModelBakery start(StateBaker baker) {
        return start(baker, NO_PACK_IDS);
    }

    @SuppressWarnings("ThreadPriorityCheck")
    public static ModelBakery start(StateBaker baker, ToIntFunction<BlockState> packIds) {
        ModelBakery bakery = new ModelBakery(baker, packIds);
        bakery.thread.setDaemon(true);
        bakery.thread.setPriority(THREAD_PRIORITY);
        bakery.thread.start();
        return bakery;
    }

    public int modelId(BlockState state) {
        Integer known = idByState.get(state);
        return known == null ? MISSING : known;
    }

    public int fluidModelId(BlockState state) {
        Integer known = fluidIdByState.get(state);
        return known == null ? MISSING : known;
    }

    public boolean positional(BlockState state) {
        return positionalStates.contains(state);
    }

    public int submergedModelId(int modelId) {
        int twin = submergedIds.get(modelId);
        return twin == MISSING ? modelId : twin;
    }

    public int oneSidedModelId(int modelId) {
        return oneSidedIds.computeIfAbsent(modelId, id -> models.register(model(id).oneSided()));
    }

    public int inwardModelId(int seabedModelId, int fluidModelId) {
        long pair = (long) seabedModelId << Integer.SIZE | (fluidModelId & LOW_WORD);
        return inwardIds.computeIfAbsent(pair,
                key -> models.register(model(seabedModelId).inward(model(fluidModelId))));
    }

    @Override
    public BakedModel model(int modelId) {
        return models.value(modelId);
    }

    @Override
    public int modelCount() {
        return models.size();
    }

    public int request(BlockState state, Runnable whenBaked) {
        int known = modelId(state);
        return known != MISSING ? known : await(new Request(state, null), () -> modelId(state), whenBaked);
    }

    public int positionalModelId(BlockState state, int blockX, int blockY, int blockZ, Runnable whenBaked) {
        Picker picker = pickers.get();
        picker.parts.clear();
        picker.random.setSeed(state.getSeed(picker.position.set(blockX, blockY, blockZ)));
        baker.pick(state, picker.random, picker.parts);

        Integer known = idByParts.get(new Request(state, picker.parts));
        if (known != null) {
            return known;
        }

        Request request = new Request(state, List.copyOf(picker.parts));
        return await(request, () -> {
            Integer published = idByParts.get(request);
            return published == null ? MISSING : published;
        }, whenBaked);
    }

    public void stop() {
        running = false;
        thread.interrupt();

        try {
            thread.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private int await(Request request, IntSupplier published, Runnable whenBaked) {
        synchronized (waiting) {
            int now = published.getAsInt();
            if (now != MISSING) {
                return now;
            }

            List<Runnable> waiters = waiting.get(request);
            if (waiters != null) {
                waiters.add(whenBaked);
                return MISSING;
            }

            waiters = new ArrayList<>();
            waiters.add(whenBaked);
            waiting.put(request, waiters);
            requests.add(request);
        }

        return MISSING;
    }

    private void serve() {
        while (running) {
            Request request;

            try {
                request = requests.take();
            } catch (InterruptedException interrupted) {
                return;
            }

            if (request.parts() == null) {
                publish(request.state(), bake(request.state()));
            } else {
                idByParts.put(request,
                        models.register(bakeParts(request).withPackId(packIds.applyAsInt(request.state()))));
            }

            answer(request);
        }
    }

    private BakedState bake(BlockState state) {
        try {
            return baker.bake(state);
        } catch (Throwable failure) {
            Eminus.LOGGER.error("Baking {} threw; the placeholder model stands in.", state, failure);
            return new BakedState(placeholder, null);
        }
    }

    private BakedModel bakeParts(Request request) {
        try {
            return baker.bakeParts(request.state(), request.parts());
        } catch (Throwable failure) {
            Eminus.LOGGER.error("Baking a variant of {} threw; the placeholder model stands in.", request.state(),
                    failure);
            return placeholder;
        }
    }

    private void publish(BlockState state, BakedState baked) {
        int statePackId = packIds.applyAsInt(state);
        int fluidPackId = packIds.applyAsInt(state.getFluidState().createLegacyBlock());
        BakedModel fluid = baked.fluid();
        int fluidId = fluid == null ? NO_FLUID : models.register(fluid.withPackId(fluidPackId));
        int blockId = baked.variants().isEmpty() ? models.register(baked.block().withPackId(statePackId))
                : registerVariants(baked, statePackId);
        if (baked.submerged() != null) {
            submergedIds.put(fluid == null ? blockId : fluidId,
                    models.register(baked.submerged().withPackId(fluid == null ? statePackId : fluidPackId)));
        }

        if (baked.positional()) {
            positionalStates.add(state);
        }

        fluidIdByState.put(state, fluidId);
        idByState.put(state, blockId);
    }

    private int registerVariants(BakedState baked, int packId) {
        List<WeightedModel> variants = baked.variants();
        int[] table = new int[variants.size() * BakedModel.VARIANT_WORDS];
        int upperBound = 0;

        for (int entry = 0; entry < variants.size(); entry++) {
            WeightedModel variant = variants.get(entry);
            upperBound += variant.weight();
            table[entry * BakedModel.VARIANT_WORDS] = upperBound;
            table[entry * BakedModel.VARIANT_WORDS + 1] = models.register(variant.model().withPackId(packId));
        }

        return models.register(variants.getFirst().model().withVariants(table).withPackId(packId));
    }

    private void answer(Request request) {
        List<Runnable> waiters;
        synchronized (waiting) {
            waiters = waiting.remove(request);
        }

        if (waiters == null) {
            return;
        }

        for (Runnable waiter : waiters) {
            try {
                waiter.run();
            } catch (Throwable failure) {
                Eminus.LOGGER.error("A waiter on the bake of {} threw.", request.state(), failure);
            }
        }
    }
}
