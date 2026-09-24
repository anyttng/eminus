package com.eminus.client.gpu.game;

import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.eminus.gpu.buffer.Buffer;
import com.eminus.gpu.buffer.Staging;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.StagingBuffer;

import org.jspecify.annotations.Nullable;

final class GameStaging implements Staging {
    private static final String MAPPED_STAGING_CLASS = "com.mojang.blaze3d.vertex.StagingBuffer$PersistentlyMapped";

    private final StagingBuffer staging;

    private GameStaging(StagingBuffer staging) {
        this.staging = staging;
    }

    static GameStaging create(String label, int bytes, boolean persistentlyMapped) {
        return new GameStaging(persistentlyMapped ? mapped(label, bytes)
                : StagingBuffer.create(label, RenderSystem.getDevice(), bytes));
    }

    private static StagingBuffer mapped(String label, int bytes) {
        try {
            Constructor<?> constructor = Class.forName(MAPPED_STAGING_CLASS)
                    .getDeclaredConstructor(String.class, int.class);
            constructor.setAccessible(true);
            return (StagingBuffer) constructor.newInstance(label, bytes);
        } catch (ReflectiveOperationException | RuntimeException refused) {
            throw new IllegalStateException("Could not build " + MAPPED_STAGING_CLASS, refused);
        }
    }

    @Override
    public Step step() {
        return new GameStep(staging.startUploading(RenderSystem.getDevice().createCommandEncoder()));
    }

    @Override
    public void close() {
        staging.close();
    }

    private record Handle(StagingBuffer.BufferHandle handle) implements Staged {
    }

    private final class GameStep implements Step {
        private final StagingBuffer.Uploader uploader;
        private final List<StagingBuffer.BufferHandle> handles = new ArrayList<>();

        private GameStep(StagingBuffer.Uploader uploader) {
            this.uploader = uploader;
        }

        @Override
        public @Nullable Staged stage(ByteBuffer bytes) {
            StagingBuffer.BufferHandle handle = staging.tryAppend(bytes);
            if (handle == null) {
                return null;
            }

            handles.add(handle);
            return new Handle(handle);
        }

        @Override
        public void copy(Staged staged, Buffer target, long offset) {
            uploader.copyTo(((Handle) staged).handle(), ((GameBuffer) target).buffer(), offset);
        }

        @Override
        public void close() {
            handles.forEach(StagingBuffer.BufferHandle::close);
            uploader.close();
        }
    }
}
