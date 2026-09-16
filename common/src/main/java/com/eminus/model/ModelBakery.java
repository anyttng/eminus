package com.eminus.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import com.eminus.Eminus;
import com.eminus.cell.Dictionary;

import net.minecraft.world.level.block.state.BlockState;

public final class ModelBakery implements ModelSource {
    public static final String THREAD_NAME = "eminus-bakery";
    public static final int MISSING = Dictionary.MISSING;
    public static final int NO_FLUID = -2;
    public static final int PLACEHOLDER_COLOUR = 0xFFFF_00FF;

    private static final int THREAD_PRIORITY = Thread.NORM_PRIORITY - 1;

    private final StateBaker baker;
    private final Dictionary<BakedModel> models = new Dictionary<>((id, model) -> { });
    private final Map<BlockState, Integer> idByState = new ConcurrentHashMap<>();
    private final Map<BlockState, Integer> fluidIdByState = new ConcurrentHashMap<>();
    private final BlockingQueue<BlockState> requests = new LinkedBlockingQueue<>();
    private final Map<BlockState, List<Runnable>> waiting = new HashMap<>();
    private final Thread thread = new Thread(this::serve, THREAD_NAME);
    private final BakedModel placeholder = BakedModel.solid(PLACEHOLDER_COLOUR);

    private volatile boolean running = true;

    private ModelBakery(StateBaker baker) {
        this.baker = baker;
    }

    public static ModelBakery start(StateBaker baker) {
        ModelBakery bakery = new ModelBakery(baker);
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
        if (known != MISSING) {
            return known;
        }

        synchronized (waiting) {
            int published = modelId(state);
            if (published != MISSING) {
                return published;
            }

            List<Runnable> waiters = waiting.get(state);
            if (waiters != null) {
                waiters.add(whenBaked);
                return MISSING;
            }

            waiters = new ArrayList<>();
            waiters.add(whenBaked);
            waiting.put(state, waiters);
            requests.add(state);
        }

        return MISSING;
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

    private void serve() {
        while (running) {
            BlockState state;

            try {
                state = requests.take();
            } catch (InterruptedException interrupted) {
                return;
            }

            publish(state, bake(state));
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

    private void publish(BlockState state, BakedState baked) {
        BakedModel fluid = baked.fluid();
        fluidIdByState.put(state, fluid == null ? NO_FLUID : models.register(fluid));
        idByState.put(state, models.register(baked.block()));

        List<Runnable> waiters;
        synchronized (waiting) {
            waiters = waiting.remove(state);
        }

        if (waiters == null) {
            return;
        }

        for (Runnable waiter : waiters) {
            try {
                waiter.run();
            } catch (Throwable failure) {
                Eminus.LOGGER.error("A waiter on the bake of {} threw.", state, failure);
            }
        }
    }
}
