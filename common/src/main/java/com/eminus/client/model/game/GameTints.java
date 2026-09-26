package com.eminus.client.model.game;

import java.util.List;

import com.eminus.model.port.BlockTints;
import com.eminus.model.port.TintSource;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

public record GameTints(BlockColors colors) implements BlockTints {
    @Override
    public List<TintSource> sources(BlockState state) {
        return colors.getTintSources(state).stream().<TintSource>map(GameTintSource::new).toList();
    }

    @Override
    public @Nullable TintSource source(BlockState state, int layer) {
        BlockTintSource source = colors.getTintSource(state, layer);
        return source == null ? null : new GameTintSource(source);
    }
}
