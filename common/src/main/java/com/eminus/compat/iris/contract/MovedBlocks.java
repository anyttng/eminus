package com.eminus.compat.iris.contract;

import com.eminus.Eminus;
import com.eminus.compat.iris.PackContract;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;

import org.jspecify.annotations.Nullable;

public final class MovedBlocks {
    private static final Splitter TOKENS = Splitter.on(CharMatcher.whitespace()).omitEmptyStrings();
    private static final int FIRST_ID = 0;

    public static IntSet parse(@Nullable String declaration) {
        if (declaration == null) {
            return IntSet.of();
        }

        IntSet ids = new IntOpenHashSet();
        for (String token : TOKENS.split(declaration)) {
            int id = id(token);
            if (id >= FIRST_ID) {
                ids.add(id);
            } else {
                Eminus.LOGGER.warn("Shader pack's {} names '{}' under {}, which is no block id: skipped",
                        PackContract.PROPERTIES_FILE, token, PackContract.MOVED_KEY);
            }
        }

        return IntSets.unmodifiable(ids);
    }

    private static int id(String token) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException notAnId) {
            return FIRST_ID - 1;
        }
    }

    private MovedBlocks() {
    }
}
