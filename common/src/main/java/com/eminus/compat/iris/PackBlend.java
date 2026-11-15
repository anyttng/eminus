package com.eminus.compat.iris;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import org.jspecify.annotations.Nullable;

record PackBlend<M, B>(@Nullable M mode, List<B> buffers) {
    static <M, B> PackBlend<M, B> of(@Nullable M ownMode, List<B> ownBuffers, @Nullable M gameMode,
            List<B> gameBuffers, ToIntFunction<B> buffer) {
        IntSet own = new IntOpenHashSet();
        ownBuffers.forEach(information -> own.add(buffer.applyAsInt(information)));
        List<B> buffers = new ArrayList<>(ownBuffers);
        gameBuffers.stream().filter(information -> !own.contains(buffer.applyAsInt(information)))
                .forEach(buffers::add);
        return new PackBlend<>(ownMode != null ? ownMode : gameMode, List.copyOf(buffers));
    }
}
